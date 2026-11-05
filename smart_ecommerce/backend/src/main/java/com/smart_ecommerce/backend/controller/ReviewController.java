package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.review.CreateReviewRequest;
import com.smart_ecommerce.backend.dto.review.ReviewResponse;
import com.smart_ecommerce.backend.dto.review.ReviewEligibilityResponse;
import com.smart_ecommerce.backend.dto.review.UpdateReviewRequest;
import com.smart_ecommerce.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/products/{productId}")
    public ResponseEntity<ReviewResponse> create(
            Authentication authentication,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reviewService.create(
                        authentication.getName(),
                        productId,
                        request
                ));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<List<ReviewResponse>> getByProduct(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(
                reviewService.getByProduct(productId)
        );
    }

    @GetMapping("/products/{productId}/eligibility")
    public ResponseEntity<ReviewEligibilityResponse> eligibility(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(reviewService.eligibility(authentication.getName(), productId));
    }

    @GetMapping("/products/{productId}/mine")
    public ResponseEntity<ReviewResponse> getMyReview(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(
                reviewService.getMyReview(
                        authentication.getName(),
                        productId
                )
        );
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ReviewResponse>> getMyReviews(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                reviewService.getMyReviews(
                        authentication.getName()
                )
        );
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> update(
            Authentication authentication,
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        return ResponseEntity.ok(
                reviewService.update(
                        authentication.getName(),
                        reviewId,
                        request
                )
        );
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID reviewId
    ) {
        reviewService.delete(
                authentication.getName(),
                reviewId
        );

        return ResponseEntity.noContent().build();
    }
}
