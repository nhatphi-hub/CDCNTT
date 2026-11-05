package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.repository.RecommendationRepository.ImplicitSignal;
import com.smart_ecommerce.backend.repository.RecommendationRepository.ProductProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplicitPreferenceModelTests {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Test
    void prefersRecentlyInteractedCategoryAndNormalizesProductTypes() {
        UUID favorite = UUID.randomUUID();
        UUID older = UUID.randomUUID();
        UUID fashion = UUID.randomUUID();
        UUID electronics = UUID.randomUUID();
        ImplicitPreferenceModel model = ImplicitPreferenceModel.from(
                List.of(new ImplicitSignal(favorite, 8, now),
                        new ImplicitSignal(older, 8, now.minusDays(60))),
                Map.of(favorite, new ProductProfile(favorite, fashion, " Shirts ", null),
                        older, new ProductProfile(older, electronics, "HEADPHONES", null)),
                now);
        assertTrue(model.score(fashion, "shirts") > model.score(electronics, "headphones"));
        assertEquals(0, model.score(UUID.randomUUID(), "unrelated"));
    }

    @Test
    void missingOrExpiredSourcesDoNotCreateFalsePreferences() {
        UUID id = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        var model = ImplicitPreferenceModel.from(
                List.of(new ImplicitSignal(id, 0, now),
                        new ImplicitSignal(UUID.randomUUID(), 20, now)),
                Map.of(id, new ProductProfile(id, category, null, null)), now);
        assertEquals(0, model.score(category, null));
        assertTrue(model.categoryIds().isEmpty());
    }

    @Test
    void categoriesStillWorkWhenProductTypeIsMissing() {
        UUID id = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        var model = ImplicitPreferenceModel.from(
                List.of(new ImplicitSignal(id, 5, now)),
                Map.of(id, new ProductProfile(id, category, null, null)), now);
        assertEquals(1.0, model.score(category, null), 0.00001);
    }
}
