package com.smart_ecommerce.backend.controller;
import com.smart_ecommerce.backend.service.SellerReviewReplyService;
import com.smart_ecommerce.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class SellerReviewReplyController {
    private final SellerReviewReplyService replies;
    private final UserService users;
    @PutMapping("/api/seller/reviews/{reviewId}/reply")
    public Map<String,Object> respond(Authentication auth,@PathVariable UUID reviewId,
                                      @RequestBody Map<String,String> request) {
        return replies.upsert(users.getUserIdByEmail(auth.getName()),reviewId,request.get("message"));
    }
    @GetMapping("/api/reviews/{reviewId}/reply")
    public Map<String,Object> view(@PathVariable UUID reviewId) {
        return replies.publicReply(reviewId);
    }
}
