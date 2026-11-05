package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.repository.RecommendationRepository.ImplicitSignal;
import com.smart_ecommerce.backend.repository.RecommendationRepository.ProductProfile;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A lightweight content-based user profile learned from implicit interactions.
 * No trained ML weights are claimed: this is an interpretable hybrid baseline.
 * Weight of an interaction decays with age (30-day half-life).
 */
final class ImplicitPreferenceModel {
    private final Map<UUID, Double> categories;
    private final Map<String, Double> types;
    private final double maxCategory;
    private final double maxType;

    private ImplicitPreferenceModel(Map<UUID, Double> categories, Map<String, Double> types) {
        this.categories = Map.copyOf(categories);
        this.types = Map.copyOf(types);
        this.maxCategory = categories.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        this.maxType = types.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    static ImplicitPreferenceModel from(
            List<ImplicitSignal> signals,
            Map<UUID, ProductProfile> productProfiles,
            LocalDateTime now
    ) {
        Map<UUID, Double> categories = new HashMap<>();
        Map<String, Double> types = new HashMap<>();
        for (ImplicitSignal signal : signals) {
            ProductProfile product = productProfiles.get(signal.productId());
            if (product == null || signal.strength() <= 0 || signal.lastAt() == null) {
                continue;
            }
            long days = Math.max(0, ChronoUnit.DAYS.between(signal.lastAt(), now));
            double decayed = signal.strength() * Math.pow(0.5, days / 30.0);
            if (product.categoryId() != null) {
                categories.merge(product.categoryId(), decayed, Double::sum);
            }
            String type = normalize(product.productType());
            if (type != null) {
                types.merge(type, decayed, Double::sum);
            }
        }
        return new ImplicitPreferenceModel(categories, types);
    }

    java.util.Set<UUID> categoryIds() { return categories.keySet(); }
    java.util.Set<String> productTypes() { return types.keySet(); }

    /** Returns [0,1]; categories and types are scored independently. */
    double score(UUID categoryId, String productType) {
        double category = categoryId == null || maxCategory == 0 ? 0
                : categories.getOrDefault(categoryId, 0.0) / maxCategory;
        String normalizedType = normalize(productType);
        double type = maxType == 0 || normalizedType == null ? 0
                : types.getOrDefault(normalizedType, 0.0) / maxType;
        if (maxCategory > 0 && maxType > 0) return 0.7 * category + 0.3 * type;
        return maxCategory > 0 ? category : type;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
