package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.recommendation.CreateRecommendationEventRequest;
import com.smart_ecommerce.backend.dto.recommendation.RecommendationEventResponse;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.OrderItem;
import com.smart_ecommerce.backend.entity.Payment;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.RecommendationEvent;
import com.smart_ecommerce.backend.entity.RecommendationEventType;
import com.smart_ecommerce.backend.entity.RecommendationType;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.RecommendationTrackingRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationTrackingService {

    private final RecommendationTrackingRepository trackingRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public RecommendationEventResponse recordEvent(
            CreateRecommendationEventRequest request,
            Authentication authentication
    ) {
        if (request.eventType() == RecommendationEventType.PURCHASE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "PURCHASE chỉ được ghi nhận từ Payment đã thanh toán");
        }

        User user = resolveCustomer(authentication);
        if (request.recommendationType() == RecommendationType.PERSONALIZED && user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Recommendation PERSONALIZED yêu cầu đăng nhập CUSTOMER");
        }
        if (user == null && request.sessionId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "sessionId là bắt buộc với sự kiện anonymous");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));

        if (request.clientEventId() != null) {
            RecommendationEvent existing = trackingRepository.findByClientEventId(
                    user == null ? null : user.getId(),
                    user == null ? request.sessionId() : null,
                    request.clientEventId());
            if (existing != null) {
                if (!sameEvent(existing, request, user)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "clientEventId đã được dùng cho một sự kiện khác");
                }
                return toResponse(existing, true);
            }
        }

        RecommendationEvent event = RecommendationEvent.builder()
                .user(user)
                .sessionId(request.sessionId())
                .product(product)
                .recommendationType(request.recommendationType())
                .eventType(request.eventType())
                .recommendationScore(request.recommendationScore())
                .recommendationPosition(request.position())
                .clientEventId(request.clientEventId())
                .createdAt(LocalDateTime.now())
                .build();
        trackingRepository.save(event);
        return toResponse(event, false);
    }

    /**
     * Last-click attribution: same user and product, with the click no later than order creation.
     * There is intentionally no lookback duration until the business requirement defines one.
     */
    @Transactional
    public void recordPurchaseConversions(Order order, Payment payment) {
        if (order == null || payment == null
                || order.getId() == null
                || order.getUser() == null
                || order.getCreatedAt() == null
                || order.getItems() == null
                || !"PAID".equalsIgnoreCase(order.getPaymentStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())
                || !"PAID".equalsIgnoreCase(payment.getStatus())
                || payment.getPaidAt() == null
                || payment.getOrder() == null
                || !order.getId().equals(payment.getOrder().getId())
                || !order.getUser().getId().equals(payment.getUser().getId())) {
            return;
        }

        List<UUID> productIds = order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        for (RecommendationEvent click : trackingRepository.findLatestClicksBefore(
                order.getUser().getId(), productIds, order.getCreatedAt())) {
            UUID productId = click.getProduct().getId();
            if (trackingRepository.existsPurchaseForOrderProduct(order.getId(), productId)) {
                continue;
            }
            RecommendationEvent purchase = RecommendationEvent.builder()
                    .user(order.getUser())
                    .sessionId(click.getSessionId())
                    .product(click.getProduct())
                    .recommendationType(click.getRecommendationType())
                    .eventType(RecommendationEventType.PURCHASE)
                    .recommendationScore(click.getRecommendationScore())
                    .recommendationPosition(click.getRecommendationPosition())
                    .order(order)
                    .attributedFrom(click)
                    .createdAt(payment.getPaidAt())
                    .build();
            trackingRepository.save(purchase);
        }
    }

    private User resolveCustomer(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
        if (!isCustomer) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ CUSTOMER mới được gửi recommendation event");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản CUSTOMER"));
    }

    private boolean sameEvent(
            RecommendationEvent existing,
            CreateRecommendationEventRequest request,
            User user
    ) {
        return Objects.equals(existing.getUser() == null ? null : existing.getUser().getId(),
                        user == null ? null : user.getId())
                && Objects.equals(existing.getSessionId(), request.sessionId())
                && existing.getProduct().getId().equals(request.productId())
                && existing.getRecommendationType() == request.recommendationType()
                && existing.getEventType() == request.eventType()
                && sameScore(existing.getRecommendationScore(), request.recommendationScore())
                && Objects.equals(existing.getRecommendationPosition(), request.position());
    }

    private boolean sameScore(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private RecommendationEventResponse toResponse(RecommendationEvent event, boolean duplicate) {
        return new RecommendationEventResponse(
                event.getId(),
                event.getProduct().getId(),
                event.getRecommendationType(),
                event.getEventType(),
                event.getCreatedAt(),
                duplicate);
    }
}
