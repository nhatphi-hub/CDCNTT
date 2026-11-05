package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.analytics.RecommendationAnalyticsOverviewResponse;
import com.smart_ecommerce.backend.dto.analytics.RecommendationProductAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.RecommendationTypeAnalyticsResponse;
import com.smart_ecommerce.backend.service.RecommendationAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics/recommendations")
@RequiredArgsConstructor
public class RecommendationAnalyticsController {

    private final RecommendationAnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<RecommendationAnalyticsOverviewResponse> overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String recommendationType
    ) {
        return ResponseEntity.ok(analyticsService.overview(from, to, recommendationType));
    }

    @GetMapping("/products")
    public ResponseEntity<List<RecommendationProductAnalyticsResponse>> products(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String recommendationType,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(
                analyticsService.products(from, to, recommendationType, limit));
    }

    @GetMapping("/types")
    public ResponseEntity<List<RecommendationTypeAnalyticsResponse>> types(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.types(from, to));
    }
}
