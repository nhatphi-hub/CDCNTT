package com.smart_ecommerce.backend.dto.review;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        UUID productId,
        String productName,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        String sellerReply,
        LocalDateTime sellerReplyAt
) {
    public ReviewResponse(UUID id,UUID userId,String userName,UUID productId,
                          String productName,Integer rating,String comment,LocalDateTime createdAt) {
        this(id,userId,userName,productId,productName,rating,comment,createdAt,null,null);
    }
}
