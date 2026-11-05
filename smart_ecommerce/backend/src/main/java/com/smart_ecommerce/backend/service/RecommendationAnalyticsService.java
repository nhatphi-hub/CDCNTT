package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.analytics.RecommendationAnalyticsOverviewResponse;
import com.smart_ecommerce.backend.dto.analytics.RecommendationProductAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.RecommendationTypeAnalyticsResponse;
import com.smart_ecommerce.backend.entity.RecommendationType;
import com.smart_ecommerce.backend.repository.RecommendationTrackingRepository;
import com.smart_ecommerce.backend.repository.RecommendationTrackingRepository.Metrics;
import com.smart_ecommerce.backend.repository.RecommendationTrackingRepository.ProductMetrics;
import com.smart_ecommerce.backend.repository.RecommendationTrackingRepository.TypeMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationAnalyticsService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int RATE_SCALE = 4;

    private final RecommendationTrackingRepository trackingRepository;

    public RecommendationAnalyticsOverviewResponse overview(
            LocalDate from,
            LocalDate to,
            String recommendationType
    ) {
        DateRange range = resolveRange(from, to);
        Metrics metrics = trackingRepository.overview(
                range.from(), range.toExclusive(), normalizeType(recommendationType));
        return new RecommendationAnalyticsOverviewResponse(
                metrics.impressions(),
                metrics.clicks(),
                metrics.views(),
                ratio(metrics.clicks(), metrics.impressions()),
                metrics.addToCarts(),
                metrics.conversions(),
                ratio(metrics.conversions(), metrics.clicks()));
    }

    public List<RecommendationProductAnalyticsResponse> products(
            LocalDate from,
            LocalDate to,
            String recommendationType,
            Integer requestedLimit
    ) {
        DateRange range = resolveRange(from, to);
        int limit = validateLimit(requestedLimit);
        return trackingRepository.products(
                        range.from(), range.toExclusive(), normalizeType(recommendationType), limit)
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    public List<RecommendationTypeAnalyticsResponse> types(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Map<RecommendationType, TypeMetrics> metricsByType = new EnumMap<>(RecommendationType.class);
        for (TypeMetrics metrics : trackingRepository.types(range.from(), range.toExclusive())) {
            try {
                metricsByType.put(RecommendationType.valueOf(metrics.recommendationType()), metrics);
            } catch (IllegalArgumentException ignored) {
                // Unknown historical values are excluded by the table check constraint.
            }
        }
        return List.of(RecommendationType.values()).stream()
                .map(type -> toTypeResponse(type, metricsByType.get(type)))
                .toList();
    }

    private RecommendationProductAnalyticsResponse toProductResponse(ProductMetrics metrics) {
        return new RecommendationProductAnalyticsResponse(
                metrics.productId(),
                metrics.productName(),
                metrics.impressions(),
                metrics.clicks(),
                metrics.views(),
                ratio(metrics.clicks(), metrics.impressions()),
                metrics.addToCarts(),
                metrics.conversions(),
                ratio(metrics.conversions(), metrics.clicks()));
    }

    private RecommendationTypeAnalyticsResponse toTypeResponse(
            RecommendationType type,
            TypeMetrics metrics
    ) {
        if (metrics == null) {
            return new RecommendationTypeAnalyticsResponse(
                    type, 0, 0, 0, BigDecimal.ZERO.setScale(RATE_SCALE), 0, 0,
                    BigDecimal.ZERO.setScale(RATE_SCALE));
        }
        return new RecommendationTypeAnalyticsResponse(
                type,
                metrics.impressions(),
                metrics.clicks(),
                metrics.views(),
                ratio(metrics.clicks(), metrics.impressions()),
                metrics.addToCarts(),
                metrics.conversions(),
                ratio(metrics.conversions(), metrics.clicks()));
    }

    private String normalizeType(String recommendationType) {
        if (recommendationType == null || recommendationType.isBlank()) {
            return null;
        }
        try {
            return RecommendationType.valueOf(
                    recommendationType.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "recommendationType chỉ chấp nhận RELATED, PERSONALIZED hoặc POPULAR");
        }
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit phải từ 1 đến " + MAX_LIMIT);
        }
        return limit;
    }

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.withDayOfMonth(1) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "from phải nhỏ hơn hoặc bằng to");
        }
        if (resolvedTo.equals(LocalDate.MAX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "to vượt quá phạm vi ngày được hỗ trợ");
        }
        return new DateRange(resolvedFrom.atStartOfDay(), resolvedTo.plusDays(1).atStartOfDay());
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDateTime from, LocalDateTime toExclusive) {
    }
}
