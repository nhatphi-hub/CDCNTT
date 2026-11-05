package com.smart_ecommerce.backend.dto.review;

public record ReviewEligibilityResponse(
        boolean purchasedAndDelivered,
        boolean alreadyReviewed,
        boolean canReview
) {}
