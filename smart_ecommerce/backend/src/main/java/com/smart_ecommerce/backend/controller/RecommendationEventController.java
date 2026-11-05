package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.recommendation.CreateRecommendationEventRequest;
import com.smart_ecommerce.backend.dto.recommendation.RecommendationEventResponse;
import com.smart_ecommerce.backend.service.RecommendationTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/events")
@RequiredArgsConstructor
public class RecommendationEventController {

    private final RecommendationTrackingService trackingService;

    @PostMapping
    public ResponseEntity<RecommendationEventResponse> record(
            @Valid @RequestBody CreateRecommendationEventRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(trackingService.recordEvent(request, authentication));
    }
}
