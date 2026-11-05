package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.review.ReviewResponse;
import com.smart_ecommerce.backend.entity.Shop;
import com.smart_ecommerce.backend.repository.ReviewRepository;
import com.smart_ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerReviewService {
    private final ShopRepository shops;
    private final ReviewRepository reviews;
    private final com.smart_ecommerce.backend.repository.ReviewReplyRepository replies;

    @Transactional(readOnly = true)
    public List<ReviewResponse> mine(UUID sellerId) {
        Shop shop = shops.findByOwnerId(sellerId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa có gian hàng"));
        if (!"ACTIVE".equalsIgnoreCase(shop.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gian hàng chưa hoạt động");
        }
        return reviews.findReviewsOfShop(shop.getId()).stream()
                .map(r -> {
                    var reply = replies.findByReviewId(r.getId());
                    return new ReviewResponse(
                        r.getId(), r.getUser().getId(), r.getUser().getFullName(),
                        r.getProduct().getId(), r.getProduct().getName(),
                        r.getRating(), r.getComment(), r.getCreatedAt(),
                        reply.map(com.smart_ecommerce.backend.entity.ReviewReply::getMessage).orElse(null),
                        reply.map(com.smart_ecommerce.backend.entity.ReviewReply::getUpdatedAt).orElse(null));
                })
                .toList();
    }
}
