package com.smart_ecommerce.backend.service;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class SellerReviewReplyService {
    private final ShopRepository shops;
    private final ReviewRepository reviews;
    private final ReviewReplyRepository replies;
    private Shop active(UUID owner) {
        Shop s=shops.findByOwnerId(owner).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN,"Chưa có shop"));
        if(!"ACTIVE".equalsIgnoreCase(s.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Shop chưa hoạt động");
        return s;
    }
    @Transactional
    public Map<String,Object> upsert(UUID owner, UUID reviewId, String message) {
        Shop shop=active(owner);
        Review review=reviews.findById(reviewId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đánh giá"));
        if(review.getProduct().getShop()==null ||
           !shop.getId().equals(review.getProduct().getShop().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đánh giá");
        String text=message==null?"":message.trim();
        if(text.isBlank() || text.length()>1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Phản hồi phải từ 1 đến 1000 ký tự");
        ReviewReply reply=replies.findByReviewId(reviewId).orElseGet(() ->
            ReviewReply.builder().review(review).shop(shop).build());
        reply.setMessage(text);
        return dto(replies.saveAndFlush(reply));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> publicReply(UUID reviewId) {
        return replies.findByReviewId(reviewId).map(this::dto).orElse(Map.of());
    }
    private Map<String,Object> dto(ReviewReply reply) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("reviewId",reply.getReview().getId());
        m.put("shopName",reply.getShop().getName());
        m.put("message",reply.getMessage());
        m.put("updatedAt",reply.getUpdatedAt());
        return m;
    }
}
