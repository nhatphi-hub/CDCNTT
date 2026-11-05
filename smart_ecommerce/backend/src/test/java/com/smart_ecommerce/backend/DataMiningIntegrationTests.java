package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.OrderItem;
import com.smart_ecommerce.backend.entity.Product;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class DataMiningIntegrationTests {

    private static final LocalDate RFM_FROM = LocalDate.of(2001, 11, 1);
    private static final LocalDate RFM_TO = LocalDate.of(2001, 12, 31);
    private static final LocalDate BASKET_DAY = LocalDate.of(2002, 3, 15);

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadRfmPageAndRfmMetricsExcludeUnpaidAndCancelledOrders() throws Exception {
        RfmFixture fixture = createRfmFixture();

        mockMvc.perform(get("/api/data-mining/rfm")
                        .param("from", RFM_FROM.toString())
                        .param("to", RFM_TO.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[?(@.customerId == '%s')].recencyDays"
                        .formatted(fixture.customerA().getId())).value(hasItem(1)))
                .andExpect(jsonPath("$.content[?(@.customerId == '%s')].frequency"
                        .formatted(fixture.customerA().getId())).value(hasItem(2)))
                .andExpect(jsonPath("$.content[?(@.customerId == '%s')].monetary"
                        .formatted(fixture.customerA().getId())).value(hasItem(300.0)))
                .andExpect(jsonPath("$.content[?(@.customerId == '%s')].rfmScore"
                        .formatted(fixture.customerA().getId())).value(hasItem("554")))
                .andExpect(jsonPath("$.content[?(@.customerId == '%s')].segment"
                        .formatted(fixture.customerA().getId())).value(hasItem("CHAMPIONS")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rfmReturnsEmptyPageWhenThereAreNoEligibleOrders() throws Exception {
        mockMvc.perform(get("/api/data-mining/rfm")
                        .param("from", "1900-01-01")
                        .param("to", "1900-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rfmUsesAnInclusiveDateRangeAndEndExclusiveTimestampBoundary() throws Exception {
        User customer = createUser("boundary");
        Product product = createProduct("boundary");
        LocalDate day = LocalDate.of(2003, 4, 10);
        createOrder(customer, "DELIVERED", "PAID", new BigDecimal("10.00"),
                day.atStartOfDay(), product);
        createOrder(customer, "DELIVERED", "PAID", new BigDecimal("20.00"),
                day.atTime(23, 59, 59, 999_999_000), product);
        createOrder(customer, "DELIVERED", "PAID", new BigDecimal("100.00"),
                day.plusDays(1).atStartOfDay(), product);
        entityManager.flush();

        mockMvc.perform(get("/api/data-mining/rfm")
                        .param("from", day.toString())
                        .param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].frequency").value(2))
                .andExpect(jsonPath("$.content[0].monetary").value(30.0))
                .andExpect(jsonPath("$.content[0].recencyDays").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadFrequentItemsAndPaidOrdersAreCountedOncePerPair() throws Exception {
        BasketFixture fixture = createBasketFixture();

        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("from", BASKET_DAY.toString())
                        .param("to", BASKET_DAY.toString())
                        .param("minSupport", "0.5")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionCount").value(2))
                .andExpect(jsonPath("$[0].support").value(0.666667))
                .andExpect(jsonPath("$[0].productName1").value(anyOf(
                        is(fixture.productOne().getName()), is(fixture.productTwo().getName()))))
                .andExpect(jsonPath("$[0].productName2").value(anyOf(
                        is(fixture.productOne().getName()), is(fixture.productTwo().getName()))))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void coOccurrenceReportsSupportAndConfidenceForEligiblePaidOrders() throws Exception {
        BasketFixture fixture = createBasketFixture();

        mockMvc.perform(get("/api/data-mining/co-occurrence")
                        .param("productId", fixture.productOne().getId().toString())
                        .param("from", BASKET_DAY.toString())
                        .param("to", BASKET_DAY.toString())
                        .param("minSupport", "0.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(fixture.productTwo().getId().toString()))
                .andExpect(jsonPath("$[0].productName").value(fixture.productTwo().getName()))
                .andExpect(jsonPath("$[0].transactionCount").value(2))
                .andExpect(jsonPath("$[0].support").value(0.666667))
                .andExpect(jsonPath("$[0].confidence").value(0.666667))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void frequentItemsReturnEmptyWhenDatasetHasNoEligibleTransactions() throws Exception {
        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("from", "1900-01-01")
                        .param("to", "1900-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void highSupportThresholdReturnsNoPairsWhenSupportIsNotReached() throws Exception {
        createBasketFixture();

        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("from", BASKET_DAY.toString())
                        .param("to", BASKET_DAY.toString())
                        .param("minSupport", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidDateSupportLimitAndPageParametersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/data-mining/rfm")
                        .param("from", "2002-03-16")
                        .param("to", BASKET_DAY.toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("minSupport", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("minSupport", "1.01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/data-mining/rfm")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dateOnlyFilterIsAcceptedForBasketAnalysis() throws Exception {
        createBasketFixture();
        mockMvc.perform(get("/api/data-mining/frequent-items")
                        .param("from", BASKET_DAY.toString())
                        .param("to", BASKET_DAY.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotReadDataMining() throws Exception {
        mockMvc.perform(get("/api/data-mining/rfm"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReadDataMining() throws Exception {
        mockMvc.perform(get("/api/data-mining/rfm"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadDataMining() throws Exception {
        mockMvc.perform(get("/api/data-mining/rfm"))
                .andExpect(status().isUnauthorized());
    }

    private RfmFixture createRfmFixture() {
        String suffix = UUID.randomUUID().toString();
        User customerA = createUser("rfm-a-" + suffix);
        User customerB = createUser("rfm-b-" + suffix);
        User customerC = createUser("rfm-c-" + suffix);
        User customerD = createUser("rfm-d-" + suffix);
        User customerE = createUser("rfm-e-" + suffix);
        Product product = createProduct("rfm-" + suffix);

        createOrder(customerA, "DELIVERED", "PAID", new BigDecimal("100.00"),
                LocalDate.of(2001, 12, 20).atTime(10, 0), product);
        createOrder(customerA, "DELIVERED", "PAID", new BigDecimal("200.00"),
                LocalDate.of(2001, 12, 30).atTime(10, 0), product);
        createOrder(customerA, "CANCELLED", "PAID", new BigDecimal("1000.00"),
                LocalDate.of(2001, 12, 31).atTime(10, 0), product);
        createOrder(customerA, "DELIVERED", "UNPAID", new BigDecimal("900.00"),
                LocalDate.of(2001, 12, 29).atTime(10, 0), product);
        createOrder(customerB, "DELIVERED", "PAID", new BigDecimal("50.00"),
                LocalDate.of(2001, 11, 30).atTime(10, 0), product);
        createOrder(customerC, "DELIVERED", "PAID", new BigDecimal("100.00"),
                LocalDate.of(2001, 12, 10).atTime(10, 0), product);
        createOrder(customerD, "DELIVERED", "PAID", new BigDecimal("500.00"),
                LocalDate.of(2001, 12, 15).atTime(10, 0), product);
        createOrder(customerE, "DELIVERED", "PAID", new BigDecimal("75.00"),
                LocalDate.of(2001, 12, 25).atTime(10, 0), product);
        entityManager.flush();
        return new RfmFixture(customerA);
    }

    private BasketFixture createBasketFixture() {
        String suffix = UUID.randomUUID().toString();
        User customerA = createUser("basket-a-" + suffix);
        User customerB = createUser("basket-b-" + suffix);
        User customerC = createUser("basket-c-" + suffix);
        User customerCancelled = createUser("basket-cancelled-" + suffix);
        User customerUnpaid = createUser("basket-unpaid-" + suffix);
        User customerBoundary = createUser("basket-boundary-" + suffix);
        Product productOne = createProduct("basket-one-" + suffix);
        Product productTwo = createProduct("basket-two-" + suffix);
        Product productThree = createProduct("basket-three-" + suffix);
        LocalDateTime insideRange = BASKET_DAY.atTime(12, 0);

        createOrder(customerA, "DELIVERED", "PAID", new BigDecimal("100.00"),
                insideRange, productOne, productTwo, productOne);
        createOrder(customerB, "DELIVERED", "PAID", new BigDecimal("100.00"),
                insideRange.plusHours(1), productOne, productTwo);
        createOrder(customerC, "DELIVERED", "PAID", new BigDecimal("100.00"),
                insideRange.plusHours(2), productOne, productThree);
        createOrder(customerCancelled, "CANCELLED", "PAID", new BigDecimal("100.00"),
                insideRange.plusHours(3), productOne, productTwo);
        createOrder(customerUnpaid, "DELIVERED", "UNPAID", new BigDecimal("100.00"),
                insideRange.plusHours(4), productOne, productTwo);
        createOrder(customerBoundary, "DELIVERED", "PAID", new BigDecimal("100.00"),
                BASKET_DAY.plusDays(1).atStartOfDay(), productOne, productTwo);
        entityManager.flush();
        return new BasketFixture(productOne, productTwo);
    }

    private User createUser(String label) {
        User user = User.builder()
                .fullName("Data mining test customer")
                .email(label.replaceAll("[^a-zA-Z0-9]", "") + "@example.test")
                .password("not-a-real-password-hash")
                .status("ACTIVE")
                .build();
        entityManager.persist(user);
        return user;
    }

    private Product createProduct(String label) {
        String normalized = label.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Product product = Product.builder()
                .name("Data mining " + label)
                .slug("dm-" + normalized + "-" + suffix)
                .sku("DM-" + suffix.substring(0, 20).toUpperCase())
                .price(new BigDecimal("100.00"))
                .quantity(100)
                .status("ACTIVE")
                .build();
        entityManager.persist(product);
        return product;
    }

    private void createOrder(
            User user,
            String status,
            String paymentStatus,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            Product... products
    ) {
        Order order = Order.builder()
                .user(user)
                .orderCode("DM-" + UUID.randomUUID())
                .subtotal(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .shippingRecipientName("Data mining test")
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
        for (Product product : products) {
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .inventoryUsesVariant(false)
                    .productNameSnapshot(product.getName())
                    .quantity(1)
                    .unitPrice(new BigDecimal("100.00"))
                    .subtotal(new BigDecimal("100.00"))
                    .build());
        }
        entityManager.persist(order);
    }

    private record RfmFixture(User customerA) {
    }

    private record BasketFixture(Product productOne, Product productTwo) {
    }
}
