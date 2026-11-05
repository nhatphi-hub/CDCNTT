package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.favorite.FavoriteResponse;
import com.smart_ecommerce.backend.entity.Favorite;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.FavoriteRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Chưa đăng nhập");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() ->
                        new IllegalArgumentException("Không tìm thấy người dùng")
                );
    }

    private FavoriteResponse toResponse(Favorite favorite) {
        Product product = favorite.getProduct();

        return new FavoriteResponse(
                favorite.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getBrand(),
                product.getPrice(),
                product.getSalePrice(),
                favorite.getCreatedAt()
        );
    }

    @Transactional
    public FavoriteResponse add(Authentication authentication, UUID productId) {
        User user = getCurrentUser(authentication);

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Không tìm thấy sản phẩm")
                );

        Favorite existing = favoriteRepository
                .findByUserIdAndProductId(user.getId(), productId)
                .orElse(null);

        if (existing != null) {
            throw new IllegalStateException(
                    "Sản phẩm đã có trong danh sách yêu thích"
            );
        }

        Favorite favorite = Favorite.builder()
                .user(user)
                .product(product)
                .build();

        return toResponse(favoriteRepository.save(favorite));
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> getMyFavorites(
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        return favoriteRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(
            Authentication authentication,
            UUID productId
    ) {
        User user = getCurrentUser(authentication);

        return favoriteRepository.existsByUserIdAndProductId(
                user.getId(),
                productId
        );
    }

    @Transactional
    public void remove(
            Authentication authentication,
            UUID productId
    ) {
        User user = getCurrentUser(authentication);

        if (!favoriteRepository.existsByUserIdAndProductId(
                user.getId(),
                productId
        )) {
            throw new IllegalArgumentException(
                    "Sản phẩm chưa có trong danh sách yêu thích"
            );
        }

        favoriteRepository.deleteByUserIdAndProductId(
                user.getId(),
                productId
        );
    }
}
