package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.recommendation.RecommendationResponse;
import com.smart_ecommerce.backend.service.RecommendationEngine;
import com.smart_ecommerce.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationEngine recommendationEngine;
    private final UserService userService;

    @GetMapping("/products/{productId}/related")
    public ResponseEntity<List<RecommendationResponse>> getRelated(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(recommendationEngine.related(productId, limit));
    }

    @GetMapping("/personalized")
    public ResponseEntity<List<RecommendationResponse>> getPersonalized(
            Authentication authentication,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        UUID authenticatedUserId = userService.getUserIdByEmail(authentication.getName());
        return ResponseEntity.ok(
                recommendationEngine.personalized(authenticatedUserId, limit));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<RecommendationResponse>> getPopular(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(recommendationEngine.popular(from, to, limit));
    }
}
