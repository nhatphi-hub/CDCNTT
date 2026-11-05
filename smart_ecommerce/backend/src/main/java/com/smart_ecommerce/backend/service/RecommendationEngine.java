package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.recommendation.RecommendationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Stable API-facing contract for replaceable recommendation strategies. */
public interface RecommendationEngine {

    List<RecommendationResponse> related(UUID productId, Integer limit);

    List<RecommendationResponse> personalized(UUID customerId, Integer limit);

    List<RecommendationResponse> popular(LocalDate from, LocalDate to, Integer limit);
}
