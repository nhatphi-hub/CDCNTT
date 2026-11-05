package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.analytics.AnalyticsOverviewResponse;
import com.smart_ecommerce.backend.dto.analytics.ProductAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.ProductViewAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.ReviewAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.SalesTrendResponse;
import com.smart_ecommerce.backend.dto.analytics.SearchAnalyticsResponse;
import com.smart_ecommerce.backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getOverview(from, to));
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductAnalyticsResponse>> getProducts(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(analyticsService.getProducts(from, to, page, size));
    }

    @GetMapping("/products/top")
    public ResponseEntity<List<ProductAnalyticsResponse>> getTopProducts(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "views") String sortBy
    ) {
        return ResponseEntity.ok(
                analyticsService.getTopProducts(from, to, limit, sortBy));
    }

    @GetMapping("/searches")
    public ResponseEntity<List<SearchAnalyticsResponse>> getSearches(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(analyticsService.getSearches(from, to, limit));
    }

    @GetMapping("/views")
    public ResponseEntity<List<ProductViewAnalyticsResponse>> getViews(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(analyticsService.getViews(from, to, limit));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ReviewAnalyticsResponse> getReviews(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getReviews(from, to));
    }

    @GetMapping("/sales")
    public ResponseEntity<List<SalesTrendResponse>> getSales(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy
    ) {
        return ResponseEntity.ok(analyticsService.getSales(from, to, groupBy));
    }
}
