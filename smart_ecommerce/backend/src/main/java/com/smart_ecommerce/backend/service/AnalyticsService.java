package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.analytics.AnalyticsOverviewResponse;
import com.smart_ecommerce.backend.dto.analytics.ProductAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.ProductViewAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.ReviewAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.SalesTrendResponse;
import com.smart_ecommerce.backend.dto.analytics.SearchAnalyticsResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.repository.AnalyticsRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");

    private final AnalyticsRepository analyticsRepository;
    private final ProductRepository productRepository;

    public AnalyticsOverviewResponse getOverview(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        return new AnalyticsOverviewResponse(
                analyticsRepository.countOrders(range.startAt(), range.endExclusive()),
                analyticsRepository.sumPaidOrderTotals(range.startAt(), range.endExclusive()),
                analyticsRepository.sumPaidOrderItemQuantities(
                        range.startAt(), range.endExclusive()),
                analyticsRepository.countProductViews(range.startAt(), range.endExclusive()),
                analyticsRepository.countSearches(range.startAt(), range.endExclusive()),
                analyticsRepository.countReviews(range.startAt(), range.endExclusive()));
    }

    public Page<ProductAnalyticsResponse> getProducts(
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        DateRange range = resolveRange(from, to);
        validatePage(page, size);

        Page<Product> products = productRepository.findAll(PageRequest.of(
                page, size, Sort.by(Sort.Direction.ASC, "name")));
        ProductMetrics metrics = getProductMetrics(
                products.getContent(), range.startAt(), range.endExclusive());
        return products.map(product -> toProductAnalytics(product, metrics));
    }

    public List<ProductAnalyticsResponse> getTopProducts(
            LocalDate from,
            LocalDate to,
            Integer requestedLimit,
            String requestedSortBy
    ) {
        DateRange range = resolveRange(from, to);
        int limit = validateLimit(requestedLimit);
        String sortBy = normalizeSortBy(requestedSortBy);

        List<UUID> productIds = switch (sortBy) {
            case "views" -> analyticsRepository.topProductIdsByViews(
                    range.startAt(), range.endExclusive(), limit);
            case "orders" -> analyticsRepository.topProductIdsBySales(
                    range.startAt(), range.endExclusive(), limit, true);
            case "quantity" -> analyticsRepository.topProductIdsBySales(
                    range.startAt(), range.endExclusive(), limit, false);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sortBy chỉ chấp nhận views, orders hoặc quantity");
        };
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Product> productsById = new HashMap<>();
        productRepository.findAllById(productIds)
                .forEach(product -> productsById.put(product.getId(), product));
        ProductMetrics metrics = getProductMetrics(
                productsById.values().stream().toList(),
                range.startAt(), range.endExclusive());

        return productIds.stream()
                .map(productsById::get)
                .filter(product -> product != null)
                .map(product -> toProductAnalytics(product, metrics))
                .toList();
    }

    public List<SearchAnalyticsResponse> getSearches(
            LocalDate from,
            LocalDate to,
            Integer requestedLimit
    ) {
        DateRange range = resolveRange(from, to);
        return analyticsRepository.topSearches(
                range.startAt(), range.endExclusive(), validateLimit(requestedLimit));
    }

    public List<ProductViewAnalyticsResponse> getViews(
            LocalDate from,
            LocalDate to,
            Integer requestedLimit
    ) {
        DateRange range = resolveRange(from, to);
        return analyticsRepository.productViews(
                range.startAt(), range.endExclusive(), validateLimit(requestedLimit));
    }

    public ReviewAnalyticsResponse getReviews(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        AnalyticsRepository.ReviewAggregate aggregate = analyticsRepository.reviewStatistics(
                range.startAt(), range.endExclusive());
        return new ReviewAnalyticsResponse(
                aggregate.totalReviews(),
                aggregate.averageRating(),
                aggregate.rating1Count(),
                aggregate.rating2Count(),
                aggregate.rating3Count(),
                aggregate.rating4Count(),
                aggregate.rating5Count());
    }

    public List<SalesTrendResponse> getSales(
            LocalDate from,
            LocalDate to,
            String requestedGroupBy
    ) {
        DateRange range = resolveRange(from, to);
        String groupBy = normalizeGroupBy(requestedGroupBy);
        Map<LocalDateTime, AnalyticsRepository.SalesAggregate> sales =
                analyticsRepository.salesByOrderDate(
                        range.startAt(), range.endExclusive(), groupBy);
        Map<LocalDateTime, Long> quantities = analyticsRepository.itemQuantitiesByOrderDate(
                range.startAt(), range.endExclusive(), groupBy);

        Map<LocalDateTime, SalesBucket> buckets = new TreeMap<>();
        sales.forEach((period, aggregate) -> buckets.put(period,
                new SalesBucket(aggregate.orderCount(), aggregate.revenue(), 0)));
        quantities.forEach((period, quantity) -> buckets.compute(period, (key, existing) -> {
            if (existing == null) {
                return new SalesBucket(0, BigDecimal.ZERO, quantity);
            }
            return new SalesBucket(existing.orderCount(), existing.revenue(), quantity);
        }));

        return buckets.entrySet().stream()
                .map(entry -> {
                    String period = "month".equals(groupBy)
                            ? YearMonth.from(entry.getKey()).format(MONTH_FORMAT)
                            : DAY_FORMAT.format(entry.getKey().toLocalDate());
                    return new SalesTrendResponse(
                            period,
                            entry.getValue().orderCount(),
                            entry.getValue().quantitySold(),
                            entry.getValue().revenue());
                })
                .toList();
    }

    private ProductMetrics getProductMetrics(
            List<Product> products,
            LocalDateTime from,
            LocalDateTime toExclusive
    ) {
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        return new ProductMetrics(
                analyticsRepository.productViewCounts(productIds, from, toExclusive),
                analyticsRepository.productSales(productIds, from, toExclusive),
                analyticsRepository.productReviews(productIds, from, toExclusive));
    }

    private ProductAnalyticsResponse toProductAnalytics(Product product, ProductMetrics metrics) {
        AnalyticsRepository.ProductSalesAggregate sales = metrics.sales().get(product.getId());
        AnalyticsRepository.ProductReviewAggregate reviews = metrics.reviews().get(product.getId());
        return new ProductAnalyticsResponse(
                product.getId(),
                product.getName(),
                metrics.viewCounts().getOrDefault(product.getId(), 0L),
                sales == null ? 0 : sales.orderCount(),
                sales == null ? 0 : sales.quantitySold(),
                reviews == null ? 0 : reviews.reviewCount(),
                reviews == null ? 0 : reviews.averageRating());
    }

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null
                ? resolvedTo.withDayOfMonth(1)
                : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "from phải nhỏ hơn hoặc bằng to");
        }
        if (resolvedTo.equals(LocalDate.MAX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "to vượt quá phạm vi ngày được hỗ trợ");
        }
        return new DateRange(
                resolvedFrom.atStartOfDay(),
                resolvedTo.plusDays(1).atStartOfDay());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page phải >= 0 và size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
    }

    private int validateLimit(Integer limit) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit phải từ 1 đến " + MAX_LIMIT);
        }
        return resolvedLimit;
    }

    private String normalizeSortBy(String sortBy) {
        String normalized = sortBy == null || sortBy.isBlank()
                ? "views"
                : sortBy.trim().toLowerCase(Locale.ROOT);
        if (!List.of("views", "orders", "quantity").contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sortBy chỉ chấp nhận views, orders hoặc quantity");
        }
        return normalized;
    }

    private String normalizeGroupBy(String groupBy) {
        String normalized = groupBy == null || groupBy.isBlank()
                ? "day"
                : groupBy.trim().toLowerCase(Locale.ROOT);
        if (!List.of("day", "month").contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "groupBy chỉ chấp nhận day hoặc month");
        }
        return normalized;
    }

    private record DateRange(
            LocalDateTime startAt,
            LocalDateTime endExclusive
    ) {
    }

    private record ProductMetrics(
            Map<UUID, Long> viewCounts,
            Map<UUID, AnalyticsRepository.ProductSalesAggregate> sales,
            Map<UUID, AnalyticsRepository.ProductReviewAggregate> reviews
    ) {
    }

    private record SalesBucket(long orderCount, BigDecimal revenue, long quantitySold) {
    }
}
