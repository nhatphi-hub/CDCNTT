package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.recommendation.RecommendationResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.repository.DataMiningRepository;
import com.smart_ecommerce.backend.repository.RecommendationRepository;
import com.smart_ecommerce.backend.repository.RecommendationRepository.ProductData;
import com.smart_ecommerce.backend.repository.RecommendationRepository.ImplicitSignal;
import com.smart_ecommerce.backend.repository.RecommendationRepository.ProductProfile;
import com.smart_ecommerce.backend.repository.RecommendationRepository.PurchaseSignal;
import com.smart_ecommerce.backend.repository.RecommendationRepository.ViewSignal;
import com.smart_ecommerce.backend.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RuleBasedRecommendationEngine implements RecommendationEngine {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_SOURCE_PRODUCTS = 20;
    private static final int MAX_CANDIDATES_PER_SIGNAL = 100;
    private static final int MAX_BATCH_CO_OCCURRENCES = 500;

    private static final double CO_OCCURRENCE_WEIGHT = 0.30;
    private static final double CATEGORY_WEIGHT = 0.18;
    private static final double PRODUCT_TYPE_WEIGHT = 0.10;
    private static final double POPULARITY_WEIGHT = 0.10;
    private static final double BEHAVIOR_WEIGHT = 0.10;
    private static final double IMPLICIT_WEIGHT = 0.32;
    private static final double SEARCH_WEIGHT = 0.18;
    private static final int MAX_IMPLICIT_SIGNALS = 40;

    private static final double RELATED_CO_OCCURRENCE_WEIGHT = 0.40;
    private static final double RELATED_CATEGORY_WEIGHT = 0.25;
    private static final double RELATED_TYPE_WEIGHT = 0.15;
    private static final double RELATED_GENDER_WEIGHT = 0.05;
    private static final double RELATED_POPULARITY_WEIGHT = 0.15;

    private static final String FREQUENTLY_BOUGHT_TOGETHER = "Frequently bought together";
    private static final String SAME_CATEGORY = "Same category";
    private static final String SIMILAR_PRODUCT_TYPE = "Similar product type";
    private static final String SIMILAR_AUDIENCE = "Similar audience";
    private static final String POPULAR_PRODUCT = "Popular product";
    private static final String PURCHASE_HISTORY = "Based on your purchase history";
    private static final String BROWSING_HISTORY = "Based on your browsing history";
    private static final String INTEREST_HISTORY = "Based on your interests";
    private static final String NEW_ARRIVAL = "New arrival";

    private final ProductRepository productRepository;
    private final RecommendationRepository recommendationRepository;
    private final DataMiningRepository dataMiningRepository;

    public RuleBasedRecommendationEngine(
            ProductRepository productRepository,
            RecommendationRepository recommendationRepository,
            DataMiningRepository dataMiningRepository
    ) {
        this.productRepository = productRepository;
        this.recommendationRepository = recommendationRepository;
        this.dataMiningRepository = dataMiningRepository;
    }

    @Override
    public List<RecommendationResponse> related(UUID productId, Integer requestedLimit) {
        int limit = validateLimit(requestedLimit);
        Product source = productRepository.findVisibleCatalogById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm đang hoạt động"));

        TimeRange allTime = allTimeRange();
        LinkedHashSet<UUID> candidateIds = new LinkedHashSet<>();
        Set<UUID> excludedIds = Set.of(productId);

        List<DataMiningRepository.CoOccurrenceAggregate> coOccurrences =
                dataMiningRepository.findActiveCoOccurrences(
                        productId, allTime.fromAt(), allTime.toExclusive(),
                        MAX_CANDIDATES_PER_SIGNAL);
        long sourceOrderCount = coOccurrences.isEmpty()
                ? 0
                : dataMiningRepository.countOrdersContainingProduct(
                        productId, allTime.fromAt(), allTime.toExclusive());
        Map<UUID, Double> coOccurrenceScores = new HashMap<>();
        for (DataMiningRepository.CoOccurrenceAggregate aggregate : coOccurrences) {
            candidateIds.add(aggregate.productId());
            coOccurrenceScores.put(aggregate.productId(),
                    ratio(aggregate.transactionCount(), sourceOrderCount));
        }

        UUID categoryId = source.getCategory() == null ? null : source.getCategory().getId();
        String productType = normalize(source.getProductType());
        String gender = normalize(source.getGender());
        if (categoryId != null) {
            candidateIds.addAll(recommendationRepository.findActiveProductIdsByCategories(
                    Set.of(categoryId), excludedIds, MAX_CANDIDATES_PER_SIGNAL));
        }
        if (productType != null) {
            candidateIds.addAll(recommendationRepository.findActiveProductIdsByTypes(
                    Set.of(productType), excludedIds, MAX_CANDIDATES_PER_SIGNAL));
        }
        if (gender != null) {
            candidateIds.addAll(recommendationRepository.findActiveProductIdsByGenders(
                    Set.of(gender), excludedIds, MAX_CANDIDATES_PER_SIGNAL));
        }
        List<RecommendationRepository.PopularProductData> popularProducts =
                recommendationRepository.findPopularProducts(
                        allTime.fromAt(), allTime.toExclusive(), MAX_CANDIDATES_PER_SIGNAL);
        popularProducts.stream()
                .map(popular -> popular.product().productId())
                .filter(id -> !excludedIds.contains(id))
                .forEach(candidateIds::add);

        candidateIds.removeAll(excludedIds);
        List<ProductData> candidates = recommendationRepository.findActiveProductDataByIds(
                candidateIds, allTime.fromAt(), allTime.toExclusive());
        List<RankedRecommendation> ranked = rankRelated(
                categoryId, productType, gender,
                candidates, coOccurrenceScores);
        return ranked.stream()
                .limit(limit)
                .map(RankedRecommendation::response)
                .toList();
    }

    @Override
    public List<RecommendationResponse> personalized(UUID customerId, Integer requestedLimit) {
        int limit = validateLimit(requestedLimit);
        TimeRange allTime = allTimeRange();
        Map<UUID, UUID> categoryRoots = recommendationRepository.findCategoryRoots();
        List<PurchaseSignal> purchases = recommendationRepository.findPurchaseSignals(
                customerId, allTime.fromAt(), allTime.toExclusive(), MAX_SOURCE_PRODUCTS);
        List<ViewSignal> views = recommendationRepository.findViewSignals(
                customerId, MAX_SOURCE_PRODUCTS);

        LinkedHashSet<UUID> purchaseIds = new LinkedHashSet<>();
        purchases.forEach(purchase -> purchaseIds.add(purchase.productId()));
        LinkedHashSet<UUID> viewIds = new LinkedHashSet<>();
        views.forEach(view -> viewIds.add(view.productId()));
        List<ImplicitSignal> implicitSignals = recommendationRepository.findImplicitSignals(
                customerId, LocalDateTime.now().minusDays(90), MAX_IMPLICIT_SIGNALS);
        LinkedHashSet<UUID> sourceIds = new LinkedHashSet<>(purchaseIds);
        sourceIds.addAll(viewIds);
        implicitSignals.forEach(signal -> sourceIds.add(signal.productId()));

        List<ProductProfile> sourceProfiles = recommendationRepository.findProfilesByIds(sourceIds);
        Map<UUID, ProductProfile> profilesById = new HashMap<>();
        sourceProfiles.forEach(profile -> profilesById.put(profile.productId(), profile));
        Map<UUID, ProductProfile> familyProfiles = new HashMap<>();
        profilesById.forEach((id, profile) -> familyProfiles.put(id,
                new ProductProfile(profile.productId(), rootOf(profile.categoryId(), categoryRoots),
                        profile.productType(), profile.gender())));
        ImplicitPreferenceModel preference = ImplicitPreferenceModel.from(
                implicitSignals, familyProfiles, LocalDateTime.now());

        Set<UUID> purchasedCategories = new HashSet<>();
        Set<String> purchasedTypes = new HashSet<>();
        Set<UUID> viewedCategories = new HashSet<>();
        Set<String> viewedTypes = new HashSet<>();
        profilesById.forEach((id, profile) -> {
            if (profile.categoryId() != null) {
                if (purchaseIds.contains(id)) {
                    purchasedCategories.add(rootOf(profile.categoryId(), categoryRoots));
                }
                if (viewIds.contains(id)) {
                    viewedCategories.add(rootOf(profile.categoryId(), categoryRoots));
                }
            }
            String type = normalize(profile.productType());
            if (type != null) {
                if (purchaseIds.contains(id)) {
                    purchasedTypes.add(type);
                }
                if (viewIds.contains(id)) {
                    viewedTypes.add(type);
                }
            }
        });

        LinkedHashSet<UUID> candidateIds = new LinkedHashSet<>();
        Map<UUID, Double> purchaseCoOccurrenceScores = new HashMap<>();
        Map<UUID, Double> viewCoOccurrenceScores = new HashMap<>();
        if (!sourceIds.isEmpty()) {
            List<DataMiningRepository.SeedCoOccurrenceAggregate> coOccurrences =
                    dataMiningRepository.findCoOccurrencesForProducts(
                            sourceIds, allTime.fromAt(), allTime.toExclusive(),
                            MAX_BATCH_CO_OCCURRENCES);
            for (DataMiningRepository.SeedCoOccurrenceAggregate aggregate : coOccurrences) {
                candidateIds.add(aggregate.productId());
                double confidence = ratio(
                        aggregate.transactionCount(), aggregate.sourceTransactionCount());
                Map<UUID, Double> targetScores = purchaseIds.contains(aggregate.sourceProductId())
                        ? purchaseCoOccurrenceScores : null;
                if (targetScores != null) {
                    targetScores.merge(aggregate.productId(), confidence, Math::max);
                }
                if (viewIds.contains(aggregate.sourceProductId())) {
                    viewCoOccurrenceScores.merge(
                            aggregate.productId(), confidence, Math::max);
                }
            }
        }

        // Include all descendant categories of the same root. A user who reads
        // novels should also see other books from nearby subcategories.
        Set<UUID> candidateCategories = new HashSet<>(purchasedCategories);
        candidateCategories.addAll(viewedCategories);
        candidateCategories.addAll(preference.categoryIds());
        Set<String> candidateTypes = new HashSet<>(purchasedTypes);
        candidateTypes.addAll(viewedTypes);
        candidateTypes.addAll(preference.productTypes());
        if (!candidateCategories.isEmpty()) {
            Set<UUID> familyCategories = new HashSet<>(candidateCategories);
            categoryRoots.forEach((categoryId, rootId) -> {
                if (candidateCategories.contains(rootId)) familyCategories.add(categoryId);
            });
            candidateIds.addAll(recommendationRepository.findActiveProductIdsByCategories(
                    familyCategories, purchaseIds, MAX_CANDIDATES_PER_SIGNAL));
        }
        if (!candidateTypes.isEmpty()) {
            candidateIds.addAll(recommendationRepository.findActiveProductIdsByTypes(
                    candidateTypes, purchaseIds, MAX_CANDIDATES_PER_SIGNAL));
        }

        // Real search history is another intent signal. Only actual visible
        // products can enter this pool, never imagined keyword matches.
        Set<UUID> searchMatches = new HashSet<>();
        recommendationRepository.findSearchKeywords(customerId, 20).stream()
                .map(String::trim).filter(term -> term.length() >= 2)
                .distinct().limit(10).forEach(term -> {
                    List<String> searches = new ArrayList<>();
                    searches.add(term);
                    if (term.toLowerCase(Locale.ROOT).contains("sách")) searches.add("book");
                    if (term.equalsIgnoreCase("thời trang")) searches.add("clothes");
                    for (String keyword : searches) {
                        for (UUID id : recommendationRepository.findActiveProductIdsByKeyword(
                                keyword, MAX_CANDIDATES_PER_SIGNAL)) {
                            searchMatches.add(id);
                            candidateIds.add(id);
                        }
                    }
                });
        List<RecommendationRepository.PopularProductData> popularProducts =
                recommendationRepository.findPopularProducts(
                        allTime.fromAt(), allTime.toExclusive(), MAX_CANDIDATES_PER_SIGNAL);
        popularProducts.stream()
                .map(popular -> popular.product().productId())
                .filter(id -> !purchaseIds.contains(id))
                .forEach(candidateIds::add);
        // Viewed/favorited items may be shown again when the catalog is small;
        // previously purchased items remain excluded.
        sourceIds.stream().filter(id -> !purchaseIds.contains(id))
                .forEach(candidateIds::add);
        candidateIds.removeAll(purchaseIds);
        if (candidateIds.size() < limit) {
            recommendationRepository.findCatalogFallback(MAX_CANDIDATES_PER_SIGNAL)
                    .stream().map(ProductData::productId)
                    .filter(id -> !purchaseIds.contains(id))
                    .forEach(candidateIds::add);
        }

        List<ProductData> candidates = recommendationRepository.findActiveProductDataByIds(
                candidateIds, allTime.fromAt(), allTime.toExclusive());
        List<RankedRecommendation> ranked = rankPersonalized(
                candidates,
                purchasedCategories,
                purchasedTypes,
                viewedTypes,
                views,
                profilesById,
                purchaseCoOccurrenceScores,
                viewCoOccurrenceScores,
                preference, categoryRoots, searchMatches);
        if (!ranked.isEmpty()) {
            return ranked.stream()
                    .limit(limit)
                    .map(RankedRecommendation::response)
                    .toList();
        }

        // A small catalog may have no *unseen* products left. Do not show an
        // empty section to an interested customer in this case: suggest
        // previously viewed/favorited (but not already purchased) products.
        // Still enforce ACTIVE product/shop scope in the repository query.
        LinkedHashSet<UUID> previouslyInterestedIds = new LinkedHashSet<>();
        implicitSignals.forEach(signal -> previouslyInterestedIds.add(signal.productId()));
        views.forEach(view -> previouslyInterestedIds.add(view.productId()));
        previouslyInterestedIds.removeAll(purchaseIds);
        if (previouslyInterestedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProductData> visibleById = new HashMap<>();
        recommendationRepository.findActiveProductDataByIds(
                previouslyInterestedIds, allTime.fromAt(), allTime.toExclusive())
                .forEach(product -> visibleById.put(product.productId(), product));
        List<RecommendationResponse> revisits = new ArrayList<>();
        for (UUID productId : previouslyInterestedIds) {
            ProductData product = visibleById.get(productId);
            if (product != null) {
                revisits.add(toResponse(product,
                        preference.score(product.categoryId(), product.productType()),
                        INTEREST_HISTORY));
                if (revisits.size() == limit) break;
            }
        }
        return revisits;
    }

    @Override
    public List<RecommendationResponse> popular(
            LocalDate from,
            LocalDate to,
            Integer requestedLimit
    ) {
        int limit = validateLimit(requestedLimit);
        TimeRange range = resolveRange(from, to);
        List<RecommendationResponse> recommendations = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        recommendationRepository.findPopularProducts(
                        range.fromAt(), range.toExclusive(), limit)
                .forEach(popular -> {
                    ProductData product = popular.product();
                    if (used.add(product.productId())) {
                        recommendations.add(toResponse(
                                product, popular.popularityScore(), POPULAR_PRODUCT));
                    }
                });
        if (recommendations.size() < limit) {
            recommendationRepository.findCatalogFallback(limit + recommendations.size())
                    .forEach(product -> {
                        if (used.add(product.productId()) && recommendations.size() < limit) {
                            recommendations.add(toResponse(product, 0, NEW_ARRIVAL));
                        }
                    });
        }
        return recommendations;
    }

    private List<RankedRecommendation> rankRelated(
            UUID sourceCategory,
            String sourceType,
            String sourceGender,
            List<ProductData> candidates,
            Map<UUID, Double> coOccurrenceScores
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        PopularityScale popularityScale = PopularityScale.from(candidates);
        boolean useCoOccurrence = coOccurrenceScores.values().stream().anyMatch(value -> value > 0);
        boolean useCategory = sourceCategory != null && candidates.stream()
                .anyMatch(candidate -> sourceCategory.equals(candidate.categoryId()));
        boolean useType = sourceType != null && candidates.stream()
                .anyMatch(candidate -> sourceType.equals(normalize(candidate.productType())));
        boolean useGender = sourceGender != null && candidates.stream()
                .anyMatch(candidate -> sourceGender.equals(normalize(candidate.gender())));
        boolean usePopularity = popularityScale.available();

        List<RankedRecommendation> ranked = new ArrayList<>(candidates.size());
        for (ProductData candidate : candidates) {
            double coOccurrence = coOccurrenceScores.getOrDefault(candidate.productId(), 0.0);
            double category = sourceCategory != null
                    && sourceCategory.equals(candidate.categoryId()) ? 1.0 : 0.0;
            double type = sourceType != null
                    && sourceType.equals(normalize(candidate.productType())) ? 1.0 : 0.0;
            double gender = sourceGender != null
                    && sourceGender.equals(normalize(candidate.gender())) ? 1.0 : 0.0;
            double popularity = popularityScale.score(candidate);

            double score = weightedScore(
                    useCoOccurrence ? new WeightedSignal(coOccurrence, RELATED_CO_OCCURRENCE_WEIGHT) : null,
                    useCategory ? new WeightedSignal(category, RELATED_CATEGORY_WEIGHT) : null,
                    useType ? new WeightedSignal(type, RELATED_TYPE_WEIGHT) : null,
                    useGender ? new WeightedSignal(gender, RELATED_GENDER_WEIGHT) : null,
                    usePopularity ? new WeightedSignal(popularity, RELATED_POPULARITY_WEIGHT) : null);
            String reason = coOccurrence > 0 ? FREQUENTLY_BOUGHT_TOGETHER
                    : category > 0 ? SAME_CATEGORY
                    : type > 0 ? SIMILAR_PRODUCT_TYPE
                    : gender > 0 ? SIMILAR_AUDIENCE
                    : POPULAR_PRODUCT;
            ranked.add(new RankedRecommendation(toResponse(candidate, score, reason), score));
        }
        return sort(ranked);
    }

    private List<RankedRecommendation> rankPersonalized(
            List<ProductData> candidates,
            Set<UUID> purchasedCategories,
            Set<String> purchasedTypes,
            Set<String> viewedTypes,
            List<ViewSignal> viewSignals,
            Map<UUID, ProductProfile> profilesById,
            Map<UUID, Double> purchaseCoOccurrenceScores,
            Map<UUID, Double> viewCoOccurrenceScores,
            ImplicitPreferenceModel preference,
            Map<UUID, UUID> categoryRoots,
            Set<UUID> searchMatches
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        PopularityScale popularityScale = PopularityScale.from(candidates);
        Map<UUID, Long> viewCounts = new HashMap<>();
        viewSignals.forEach(view -> viewCounts.put(view.productId(), view.viewCount()));
        long maxViewCount = viewSignals.stream().mapToLong(ViewSignal::viewCount).max().orElse(0);

        boolean usePurchaseCoOccurrence = purchaseCoOccurrenceScores.values().stream()
                .anyMatch(value -> value > 0);
        boolean useCategory = !purchasedCategories.isEmpty() && candidates.stream()
                .anyMatch(candidate -> purchasedCategories.contains(rootOf(candidate.categoryId(), categoryRoots)));
        boolean useType = !purchasedTypes.isEmpty() && candidates.stream()
                .anyMatch(candidate -> purchasedTypes.contains(normalize(candidate.productType())));
        boolean usePopularity = popularityScale.available();
        boolean useImplicit = candidates.stream().anyMatch(candidate ->
                preference.score(rootOf(candidate.categoryId(), categoryRoots), candidate.productType()) > 0);
        boolean useSearch = !searchMatches.isEmpty();
        boolean useBehavior = !viewSignals.isEmpty() && candidates.stream().anyMatch(candidate ->
                behaviorScore(candidate, viewedTypes, viewSignals,
                        viewCounts, maxViewCount, profilesById, viewCoOccurrenceScores, categoryRoots) > 0);

        List<RankedRecommendation> ranked = new ArrayList<>(candidates.size());
        for (ProductData candidate : candidates) {
            double purchaseCoOccurrence = purchaseCoOccurrenceScores
                    .getOrDefault(candidate.productId(), 0.0);
            double category = purchasedCategories.contains(rootOf(candidate.categoryId(), categoryRoots)) ? 1.0 : 0.0;
            double type = purchasedTypes.contains(normalize(candidate.productType())) ? 1.0 : 0.0;
            double behavior = behaviorScore(
                    candidate, viewedTypes, viewSignals,
                    viewCounts, maxViewCount, profilesById, viewCoOccurrenceScores, categoryRoots);
            double popularity = popularityScale.score(candidate);
            double interest = preference.score(rootOf(candidate.categoryId(), categoryRoots), candidate.productType());
            double search = searchMatches.contains(candidate.productId()) ? 1.0 : 0.0;

            double score = weightedScore(
                    usePurchaseCoOccurrence
                            ? new WeightedSignal(purchaseCoOccurrence, CO_OCCURRENCE_WEIGHT) : null,
                    useCategory ? new WeightedSignal(category, CATEGORY_WEIGHT) : null,
                    useType ? new WeightedSignal(type, PRODUCT_TYPE_WEIGHT) : null,
                    usePopularity ? new WeightedSignal(popularity, POPULARITY_WEIGHT) : null,
                    useBehavior ? new WeightedSignal(behavior, BEHAVIOR_WEIGHT) : null,
                    useImplicit ? new WeightedSignal(interest, IMPLICIT_WEIGHT) : null,
                    useSearch ? new WeightedSignal(search, SEARCH_WEIGHT) : null);
            String reason = purchaseCoOccurrence > 0 ? FREQUENTLY_BOUGHT_TOGETHER
                    : category > 0 ? PURCHASE_HISTORY
                    : type > 0 ? PURCHASE_HISTORY
                    : interest > 0 || search > 0 ? INTEREST_HISTORY
                    : behavior > 0 ? BROWSING_HISTORY
                    : POPULAR_PRODUCT;
            ranked.add(new RankedRecommendation(toResponse(candidate, score, reason), score));
        }
        return sort(ranked);
    }

    private double behaviorScore(
            ProductData candidate,
            Set<String> viewedTypes,
            List<ViewSignal> viewSignals,
            Map<UUID, Long> viewCounts,
            long maxViewCount,
            Map<UUID, ProductProfile> profilesById,
            Map<UUID, Double> viewCoOccurrenceScores,
            Map<UUID, UUID> categoryRoots
    ) {
        double score = viewCoOccurrenceScores.getOrDefault(candidate.productId(), 0.0);
        for (ViewSignal view : viewSignals) {
            ProductProfile profile = profilesById.get(view.productId());
            if (profile == null) {
                continue;
            }
            double strength = maxViewCount == 0 ? 0
                    : ratio(viewCounts.getOrDefault(view.productId(), 0L), maxViewCount);
            if (profile.categoryId() != null
                    && rootOf(profile.categoryId(), categoryRoots)
                        .equals(rootOf(candidate.categoryId(), categoryRoots))) {
                score = Math.max(score, strength);
            }
            if (normalize(profile.productType()) != null
                    && viewedTypes.contains(normalize(candidate.productType()))
                    && normalize(profile.productType()).equals(normalize(candidate.productType()))) {
                score = Math.max(score, strength * 0.5);
            }
        }
        return Math.min(score, 1.0);
    }

    private static List<RankedRecommendation> sort(List<RankedRecommendation> recommendations) {
        return recommendations.stream()
                .sorted(Comparator.comparingDouble(RankedRecommendation::score).reversed()
                        .thenComparing(item -> item.response().productName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.response().productId()))
                .toList();
    }

    private static RecommendationResponse toResponse(
            ProductData product,
            double recommendationScore,
            String reason
    ) {
        return new RecommendationResponse(
                product.productId(), product.productName(), product.slug(), product.imageUrl(),
                product.price(), product.salePrice(), score(recommendationScore), reason);
    }

    private static BigDecimal score(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(value, 1)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0 : Math.min(1.0, (double) numerator / denominator);
    }

    private static double weightedScore(WeightedSignal... signals) {
        double weightedTotal = 0;
        double activeWeight = 0;
        for (WeightedSignal signal : signals) {
            if (signal != null) {
                weightedTotal += signal.score() * signal.weight();
                activeWeight += signal.weight();
            }
        }
        return activeWeight == 0 ? 0 : weightedTotal / activeWeight;
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit phải từ 1 đến " + MAX_LIMIT);
        }
        return limit;
    }

    private TimeRange resolveRange(LocalDate from, LocalDate to) {
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
        return new TimeRange(resolvedFrom.atStartOfDay(), resolvedTo.plusDays(1).atStartOfDay());
    }

    private static TimeRange allTimeRange() {
        return new TimeRange(
                LocalDate.of(1, 1, 1).atStartOfDay(), LocalDateTime.now());
    }

    private static UUID rootOf(UUID categoryId, Map<UUID, UUID> roots) {
        return categoryId == null ? null : roots.getOrDefault(categoryId, categoryId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private record TimeRange(LocalDateTime fromAt, LocalDateTime toExclusive) {
    }

    private record WeightedSignal(double score, double weight) {
    }

    private record RankedRecommendation(RecommendationResponse response, double score) {
    }

    private record PopularityScale(long maxUnitsSold, long maxViews) {
        private static PopularityScale from(List<ProductData> products) {
            return new PopularityScale(
                    products.stream().mapToLong(ProductData::unitsSold).max().orElse(0),
                    products.stream().mapToLong(ProductData::viewCount).max().orElse(0));
        }

        private boolean available() {
            return maxUnitsSold > 0 || maxViews > 0;
        }

        private double score(ProductData product) {
            double sales = maxUnitsSold == 0 ? 0
                    : (double) product.unitsSold() / maxUnitsSold;
            double views = maxViews == 0 ? 0
                    : (double) product.viewCount() / maxViews;
            if (maxUnitsSold > 0 && maxViews > 0) {
                return (sales + views) / 2;
            }
            return maxUnitsSold > 0 ? sales : views;
        }
    }
}
