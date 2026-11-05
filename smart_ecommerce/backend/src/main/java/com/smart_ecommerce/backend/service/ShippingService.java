package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.shipping.CreateShippingRequest;
import com.smart_ecommerce.backend.dto.shipping.ShippingResponse;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.Shipping;
import com.smart_ecommerce.backend.repository.OrderRepository;
import com.smart_ecommerce.backend.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ShippingService {

    private static final List<String> ACTIVE_ORDER_STATUSES = List.of("CONFIRMED", "SHIPPING");

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public ShippingResponse create(UUID orderId, CreateShippingRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!ACTIVE_ORDER_STATUSES.contains(normalize(order.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ tạo vận đơn cho đơn hàng CONFIRMED hoặc SHIPPING");
        }
        if (!"COD".equalsIgnoreCase(order.getPaymentMethod())
                && !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn thanh toán trực tuyến phải được thanh toán trước khi tạo vận đơn");
        }
        if (shippingRepository.existsByOrder_Id(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đơn hàng đã có vận đơn");
        }

        String requestedTrackingCode = request == null ? null : request.trackingCode();
        String trackingCode = requestedTrackingCode == null || requestedTrackingCode.isBlank()
                ? generateTrackingCode()
                : requestedTrackingCode.trim();
        if (shippingRepository.existsByTrackingCodeIgnoreCase(trackingCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "trackingCode đã được sử dụng");
        }

        if ("CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            orderService.updateStatus(orderId, "SHIPPING");
        }

        Shipping shipping = Shipping.builder()
                .order(order)
                .trackingCode(trackingCode)
                .shippingFee(order.getShippingFee())
                .shippingStatus("READY")
                .build();
        return toResponse(shippingRepository.save(shipping));
    }

    @Transactional(readOnly = true)
    public List<ShippingResponse> getAllForManagement() {
        return shippingRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShippingResponse getByOrder(UUID orderId, UUID userId, boolean manager) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!manager && !order.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng");
        }
        return toResponse(shippingRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Đơn hàng chưa có vận đơn")));
    }

    @Transactional(readOnly = true)
    public ShippingResponse getById(UUID shippingId, UUID userId, boolean manager) {
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn"));
        if (!manager && !shipping.getOrder().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn");
        }
        return toResponse(shipping);
    }

    public ShippingResponse updateStatus(UUID shippingId, String requestedStatus) {
        String newStatus = normalize(requestedStatus);
        if (!List.of("READY", "IN_TRANSIT", "DELIVERED", "FAILED").contains(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "shippingStatus phải là READY, IN_TRANSIT, DELIVERED hoặc FAILED");
        }

        Shipping shipping = shippingRepository.findByIdForUpdate(shippingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn"));
        String currentStatus = normalize(shipping.getShippingStatus());
        if (currentStatus.equals(newStatus)) {
            return toResponse(shipping);
        }
        if (!isAllowedTransition(currentStatus, newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể chuyển trạng thái vận đơn từ "
                            + currentStatus + " sang " + newStatus);
        }

        UUID orderId = shipping.getOrder().getId();
        if ("IN_TRANSIT".equals(newStatus)) {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
            String orderStatus = normalize(order.getStatus());
            if ("CONFIRMED".equals(orderStatus)) {
                orderService.updateStatus(orderId, "SHIPPING");
            } else if (!"SHIPPING".equals(orderStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Đơn hàng phải ở trạng thái CONFIRMED hoặc SHIPPING trước khi vận chuyển");
            }
        } else if ("DELIVERED".equals(newStatus)) {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
            String orderStatus = normalize(order.getStatus());
            if ("SHIPPING".equals(orderStatus)) {
                orderService.updateStatus(orderId, "DELIVERED");
            } else if (!"DELIVERED".equals(orderStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Đơn hàng phải ở trạng thái SHIPPING trước khi xác nhận giao thành công");
            }
        }

        shipping.setShippingStatus(newStatus);
        return toResponse(shippingRepository.save(shipping));
    }

    public ShippingResponse updateTrackingCode(UUID shippingId, String requestedTrackingCode) {
        String trackingCode = requestedTrackingCode == null
                ? ""
                : requestedTrackingCode.trim();
        if (trackingCode.isBlank() || trackingCode.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "trackingCode là bắt buộc và tối đa 100 ký tự");
        }

        Shipping shipping = shippingRepository.findByIdForUpdate(shippingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn"));
        if ("DELIVERED".equalsIgnoreCase(shipping.getShippingStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Không thể thay đổi mã vận đơn đã giao thành công");
        }
        if (shippingRepository.existsByTrackingCodeIgnoreCaseAndIdNot(
                trackingCode, shippingId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "trackingCode đã được sử dụng");
        }
        shipping.setTrackingCode(trackingCode);
        return toResponse(shippingRepository.save(shipping));
    }

    private boolean isAllowedTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case "PENDING" -> "READY".equals(newStatus);
            case "READY" -> List.of("IN_TRANSIT", "FAILED").contains(newStatus);
            case "IN_TRANSIT" -> List.of("DELIVERED", "FAILED").contains(newStatus);
            case "FAILED" -> "IN_TRANSIT".equals(newStatus);
            default -> false;
        };
    }

    private String generateTrackingCode() {
        String trackingCode;
        do {
            trackingCode = "MOCK-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        } while (shippingRepository.existsByTrackingCodeIgnoreCase(trackingCode));
        return trackingCode;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ShippingResponse toResponse(Shipping shipping) {
        Order order = shipping.getOrder();
        return new ShippingResponse(
                shipping.getId(),
                order.getId(),
                order.getOrderCode(),
                shipping.getTrackingCode(),
                shipping.getShippingFee(),
                shipping.getShippingStatus(),
                shipping.getCreatedAt(),
                shipping.getUpdatedAt());
    }
}
