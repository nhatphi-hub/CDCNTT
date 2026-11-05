package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.productview.ProductViewRequest;
import com.smart_ecommerce.backend.dto.productview.ProductViewResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductView;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductViewRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductViewService {

    private final ProductViewRepository productViewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProductViewResponse recordView(UUID productId, ProductViewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));

        User user = getCurrentUser();

        int duration = request == null || request.durationSeconds() == null
                ? 0
                : request.durationSeconds();

        ProductView view = ProductView.builder()
                .user(user)
                .product(product)
                .viewedAt(LocalDateTime.now())
                .durationSeconds(duration)
                .build();

        return toResponse(productViewRepository.save(view));
    }

    @Transactional
    public List<ProductViewResponse> getMyHistory() {
        User user = getCurrentUser();
        return productViewRepository.findByUserIdOrderByViewedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public long getMyViewCount() {
        return productViewRepository.countByUserId(getCurrentUser().getId());
    }

    @Transactional
    public long getProductViewCount(UUID productId) {
        ensureProductExists(productId);
        return productViewRepository.countByProductId(productId);
    }

    @Transactional
    public List<ProductViewResponse> getProductViewsForAdmin(UUID productId) {
        ensureAdmin();
        ensureProductExists(productId);
        return productViewRepository.findByProductIdOrderByViewedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void clearMyHistory() {
        productViewRepository.deleteByUserId(getCurrentUser().getId());
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
    }

    private void ensureAdmin() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ ADMIN mới được xem dữ liệu lượt xem chi tiết");
        }
    }

    private void ensureProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
    }

    private ProductViewResponse toResponse(ProductView view) {
        Product product = view.getProduct();

        return new ProductViewResponse(
                view.getId(),
                view.getUser() != null ? view.getUser().getId() : null,
                product.getId(),
                product.getName(),
                product.getSlug(),
                view.getViewedAt(),
                view.getDurationSeconds()
        );
    }
}
