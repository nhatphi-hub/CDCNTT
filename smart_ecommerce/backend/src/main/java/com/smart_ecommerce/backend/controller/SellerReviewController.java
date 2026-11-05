package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.review.ReviewResponse;
import com.smart_ecommerce.backend.service.SellerReviewService;
import com.smart_ecommerce.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seller/reviews")
@RequiredArgsConstructor
public class SellerReviewController {
    private final SellerReviewService reviews;
    private final UserService users;

    @GetMapping
    public List<ReviewResponse> mine(Authentication authentication) {
        return reviews.mine(users.getUserIdByEmail(authentication.getName()));
    }
}
