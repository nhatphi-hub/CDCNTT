package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.OrderItem;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductView;
import com.smart_ecommerce.backend.entity.Review;
import com.smart_ecommerce.backend.entity.SearchHistory;
import com.smart_ecommerce.backend.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AnalyticsIntegrationTests {

    private static final LocalDate TEST_DAY = LocalDate.of(2025, 4, 10);

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadOverview() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overviewAggregatesPaidSalesAndEventsInsideInclusiveDateRange() throws Exception {
        createAnalyticsFixture();

        mockMvc.perform(get("/api/analytics/overview")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.totalRevenue").value(1150.0))
                .andExpect(jsonPath("$.totalItemsSold").value(2))
                .andExpect(jsonPath("$.totalProductViews").value(2))
                .andExpect(jsonPath("$.totalSearches").value(3))
                .andExpect(jsonPath("$.totalReviews").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidDateRangeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .param("from", "2025-04-11")
                        .param("to", TEST_DAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void productAnalyticsAggregatesMetricsAndUsesPageResponse() throws Exception {
        Fixture fixture = createAnalyticsFixture();

        mockMvc.perform(get("/api/analytics/products")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString())
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.productId == '%s')].viewCount"
                        .formatted(fixture.product().getId())).value(hasItem(2)))
                .andExpect(jsonPath("$.content[?(@.productId == '%s')].orderCount"
                        .formatted(fixture.product().getId())).value(hasItem(1)))
                .andExpect(jsonPath("$.content[?(@.productId == '%s')].quantitySold"
                        .formatted(fixture.product().getId())).value(hasItem(2)))
                .andExpect(jsonPath("$.content[?(@.productId == '%s')].reviewCount"
                        .formatted(fixture.product().getId())).value(hasItem(2)))
                .andExpect(jsonPath("$.content[?(@.productId == '%s')].averageRating"
                        .formatted(fixture.product().getId())).value(hasItem(4.5)));

        mockMvc.perform(get("/api/analytics/products")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.numberOfElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void topProductsAndViewsReturnAggregatesWithoutCustomerIdentity() throws Exception {
        Fixture fixture = createAnalyticsFixture();

        mockMvc.perform(get("/api/analytics/products/top")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString())
                        .param("sortBy", "views")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(fixture.product().getId().toString()));

        mockMvc.perform(get("/api/analytics/views")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString())
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(fixture.product().getId().toString()))
                .andExpect(jsonPath("$[0].viewCount").value(2))
                .andExpect(content().string(not(containsString("userId"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchAnalyticsGroupsTrimmedCaseInsensitiveKeywords() throws Exception {
        createAnalyticsFixture();

        mockMvc.perform(get("/api/analytics/searches")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString())
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyword").value("shirt"))
                .andExpect(jsonPath("$[0].searchCount").value(2))
                .andExpect(content().string(not(containsString("userId"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reviewAnalyticsReturnsAverageAndRatingDistribution() throws Exception {
        createAnalyticsFixture();

        mockMvc.perform(get("/api/analytics/reviews")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.rating4Count").value(1))
                .andExpect(jsonPath("$.rating5Count").value(1))
                .andExpect(jsonPath("$.rating1Count").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void salesTrendAggregatesOrdersQuantitiesAndRevenueByDayAndMonth() throws Exception {
        createAnalyticsFixture();

        mockMvc.perform(get("/api/analytics/sales")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString())
                        .param("groupBy", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("2025-04-10"))
                .andExpect(jsonPath("$[0].orderCount").value(1))
                .andExpect(jsonPath("$[0].quantitySold").value(2))
                .andExpect(jsonPath("$[0].revenue").value(1150.0))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(get("/api/analytics/sales")
                        .param("from", TEST_DAY.toString())
                        .param("to", TEST_DAY.toString())
                        .param("groupBy", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("2025-04"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidAnalyticsFiltersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/analytics/products/top").param("sortBy", "revenue"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/sales").param("groupBy", "year"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/products").param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/searches").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/overview").param("from", "invalid"))
                .andExpect(status().isBadRequest());
    }

    private Fixture createAnalyticsFixture() {
        String suffix = UUID.randomUUID().toString();
        LocalDateTime dayStart = TEST_DAY.atStartOfDay();
        User user = User.builder()
                .fullName("Analytics test user")
                .email("analytics-" + suffix + "@example.test")
                .password("not-a-real-password-hash")
                .status("ACTIVE")
                .build();
        User secondReviewer = User.builder()
                .fullName("Analytics second reviewer")
                .email("analytics-review-" + suffix + "@example.test")
                .password("not-a-real-password-hash")
                .status("ACTIVE")
                .build();
        Product product = Product.builder()
                .name("Analytics test product")
                .slug("analytics-" + suffix)
                .sku("ANALYTICS-" + suffix.substring(0, 12).toUpperCase())
                .price(new BigDecimal("500.00"))
                .quantity(100)
                .status("ACTIVE")
                .build();
        entityManager.persist(user);
        entityManager.persist(secondReviewer);
        entityManager.persist(product);

        createOrder(user, product, "DELIVERED", "PAID", 2,
                new BigDecimal("1150.00"), dayStart.plusHours(12), suffix + "-PAID");
        createOrder(user, product, "PENDING", "UNPAID", 4,
                new BigDecimal("2000.00"), dayStart.plusHours(13), suffix + "-UNPAID");
        createOrder(user, product, "CANCELLED", "UNPAID", 1,
                new BigDecimal("500.00"), dayStart.plusHours(14), suffix + "-CANCELLED");

        entityManager.persist(ProductView.builder()
                .user(user).product(product).viewedAt(dayStart)
                .durationSeconds(5).build());
        entityManager.persist(ProductView.builder()
                .user(user).product(product).viewedAt(dayStart.plusHours(23).plusMinutes(59))
                .durationSeconds(10).build());
        entityManager.persist(SearchHistory.builder()
                .user(user).keyword(" Shirt ").searchedAt(dayStart.plusHours(1)).build());
        entityManager.persist(SearchHistory.builder()
                .user(user).keyword("shirt").searchedAt(dayStart.plusHours(2)).build());
        entityManager.persist(SearchHistory.builder()
                .user(user).keyword("pants").searchedAt(dayStart.plusHours(3)).build());
        entityManager.persist(Review.builder()
                .user(user).product(product).rating(4).comment("Good").createdAt(dayStart).build());
        entityManager.persist(Review.builder()
                .user(secondReviewer).product(product).rating(5).comment("Great")
                .createdAt(dayStart.plusHours(23)).build());
        entityManager.flush();
        return new Fixture(product);
    }

    private void createOrder(
            User user,
            Product product,
            String status,
            String paymentStatus,
            int quantity,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            String orderCode
    ) {
        BigDecimal itemSubtotal = new BigDecimal("500.00")
                .multiply(BigDecimal.valueOf(quantity));
        Order order = Order.builder()
                .user(user)
                .orderCode(orderCode)
                .subtotal(itemSubtotal)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(totalAmount.subtract(itemSubtotal))
                .totalAmount(totalAmount)
                .shippingRecipientName("Analytics Test")
                .shippingPhone("0900000000")
                .shippingProvince("Test Province")
                .shippingDistrict("Test District")
                .shippingWard("Test Ward")
                .shippingAddressDetail("Test Address")
                .status(status)
                .paymentMethod("COD")
                .paymentStatus(paymentStatus)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        order.getItems().add(OrderItem.builder()
                .order(order)
                .product(product)
                .inventoryUsesVariant(false)
                .productNameSnapshot(product.getName())
                .quantity(quantity)
                .unitPrice(new BigDecimal("500.00"))
                .subtotal(itemSubtotal)
                .build());
        entityManager.persist(order);
    }

    private record Fixture(Product product) {
    }
}
