package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.recommendation.CreateRecommendationEventRequest;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.OrderItem;
import com.smart_ecommerce.backend.entity.Payment;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.RecommendationEvent;
import com.smart_ecommerce.backend.entity.RecommendationEventType;
import com.smart_ecommerce.backend.entity.RecommendationType;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.service.PaymentService;
import com.smart_ecommerce.backend.service.RecommendationTrackingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class RecommendationTrackingIntegrationTests {

    private static final String CUSTOMER_EMAIL = "recommendation-tracking-customer@example.test";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RecommendationTrackingService trackingService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void customerCanTrackClickAndIdentityAndTimestampComeFromServer() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Product product = createProduct("click-server-values");
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        String body = """
                {"recommendationType":"PERSONALIZED","productId":"%s",
                 "eventType":"CLICK","recommendationScore":0.82,"position":2,
                 "userId":"%s","customerId":"%s","createdAt":"2000-01-01T00:00:00"}
                """.formatted(product.getId(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("CLICK"))
                .andExpect(jsonPath("$.duplicate").value(false));

        RecommendationEvent event = entityManager.createQuery("""
                        select event from RecommendationEvent event
                        where event.product.id = :productId and event.eventType = :eventType
                        """, RecommendationEvent.class)
                .setParameter("productId", product.getId())
                .setParameter("eventType", RecommendationEventType.CLICK)
                .getSingleResult();
        assertEquals(customer.getId(), event.getUser().getId());
        assertTrue(!event.getCreatedAt().isBefore(before));
        assertTrue(!event.getCreatedAt().isAfter(LocalDateTime.now().plusSeconds(1)));
        assertEquals(new BigDecimal("0.82"), event.getRecommendationScore());
        assertEquals(2, event.getRecommendationPosition());
    }

    @Test
    void anonymousRelatedEventRequiresAndStoresSessionId() throws Exception {
        Product product = createProduct("anonymous-session");
        UUID sessionId = UUID.randomUUID();
        CreateRecommendationEventRequest request = request(
                RecommendationType.RELATED, product, RecommendationEventType.IMPRESSION,
                null, 1, sessionId, null);

        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk());

        RecommendationEvent event = entityManager.createQuery("""
                        select event from RecommendationEvent event where event.sessionId = :sessionId
                        """, RecommendationEvent.class)
                .setParameter("sessionId", sessionId)
                .getSingleResult();
        assertEquals(null, event.getUser());
        assertEquals(sessionId, event.getSessionId());
    }

    @Test
    void anonymousEventWithoutSessionIsRejected() throws Exception {
        Product product = createProduct("anonymous-no-session");
        postEvent(request(RecommendationType.POPULAR, product,
                RecommendationEventType.CLICK, null, null, null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIsRejected() throws Exception {
        postEvent(new CreateRecommendationEventRequest(
                RecommendationType.RELATED, UUID.randomUUID(), RecommendationEventType.CLICK,
                null, null, UUID.randomUUID(), null))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRecommendationAndEventTypesAreRejected() throws Exception {
        String productId = createProduct("invalid-enum").getId().toString();
        String badRecommendation = """
                {"recommendationType":"SUGGESTED","productId":"%s","eventType":"CLICK","sessionId":"%s"}
                """.formatted(productId, UUID.randomUUID());
        String badEvent = """
                {"recommendationType":"RELATED","productId":"%s","eventType":"OPENED","sessionId":"%s"}
                """.formatted(productId, UUID.randomUUID());

        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON).content(badRecommendation))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON).content(badEvent))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientCannotCreatePurchaseEvent() throws Exception {
        Product product = createProduct("client-purchase");
        postEvent(request(RecommendationType.RELATED, product,
                RecommendationEventType.PURCHASE, null, null, UUID.randomUUID(), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scoreBelowZeroIsRejected() throws Exception {
        Product product = createProduct("score-low");
        postEvent(request(RecommendationType.RELATED, product, RecommendationEventType.CLICK,
                new BigDecimal("-0.01"), null, UUID.randomUUID(), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scoreAboveOneIsRejected() throws Exception {
        Product product = createProduct("score-high");
        postEvent(request(RecommendationType.RELATED, product, RecommendationEventType.CLICK,
                new BigDecimal("1.01"), null, UUID.randomUUID(), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void positionBelowOneIsRejected() throws Exception {
        Product product = createProduct("position-low");
        postEvent(request(RecommendationType.RELATED, product, RecommendationEventType.CLICK,
                null, 0, UUID.randomUUID(), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void clientEventIdMakesExactRetryIdempotentAndRejectsReuseForDifferentPayload()
            throws Exception {
        createCustomer(CUSTOMER_EMAIL);
        Product first = createProduct("idempotency-first");
        Product second = createProduct("idempotency-second");
        UUID clientEventId = UUID.randomUUID();
        CreateRecommendationEventRequest request = request(
                RecommendationType.PERSONALIZED, first, RecommendationEventType.CLICK,
                new BigDecimal("0.5"), 1, null, clientEventId);
        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        mockMvc.perform(post("/api/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(
                                RecommendationType.PERSONALIZED, second,
                                RecommendationEventType.CLICK, new BigDecimal("0.5"),
                                1, null, clientEventId))))
                .andExpect(status().isConflict());
        assertEquals(1, countEvents());
    }

    @Test
    @WithMockUser(username = "tracking-staff@example.test", roles = "STAFF")
    void staffCannotTrackPersonalizedOrOtherRecommendationEvents() throws Exception {
        Product product = createProduct("staff-event");
        postEvent(request(RecommendationType.PERSONALIZED, product,
                RecommendationEventType.CLICK, null, 1, UUID.randomUUID(), null))
                .andExpect(status().isForbidden());
        postEvent(request(RecommendationType.RELATED, product,
                RecommendationEventType.CLICK, null, 1, UUID.randomUUID(), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotTrackPersonalizedRecommendation() throws Exception {
        Product product = createProduct("anonymous-personalized");
        postEvent(request(RecommendationType.PERSONALIZED, product,
                RecommendationEventType.CLICK, null, 1, UUID.randomUUID(), null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void adminCanReadRecommendationAnalytics() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/recommendations/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/recommendations/types"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "tracking-staff@example.test", roles = "STAFF")
    void staffCannotReadRecommendationAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/recommendations/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void customerCannotReadRecommendationAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/recommendations/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadRecommendationAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/recommendations/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void invalidDatesAndRecommendationTypeAreRejected() throws Exception {
        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("from", "2026-09-30").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("recommendationType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/recommendations/products")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void overviewAggregatesImpressionsClicksCtrAddToCartsAndPaidConversions() throws Exception {
        User customer = createCustomer("tracking-metrics@example.test");
        Product productA = createProduct("metrics-a");
        Product productB = createProduct("metrics-b");
        LocalDateTime clickedAt = LocalDateTime.now().minusMinutes(5);
        createEvent(customer, productA, RecommendationType.PERSONALIZED,
                RecommendationEventType.IMPRESSION, null, 1, clickedAt.minusSeconds(1));
        RecommendationEvent click = createEvent(customer, productA, RecommendationType.PERSONALIZED,
                RecommendationEventType.CLICK, new BigDecimal("0.8"), 1, clickedAt);
        createEvent(customer, productA, RecommendationType.PERSONALIZED,
                RecommendationEventType.ADD_TO_CART, new BigDecimal("0.8"), 1,
                clickedAt.plusSeconds(1));
        createEvent(customer, productA, RecommendationType.PERSONALIZED,
                RecommendationEventType.VIEW, new BigDecimal("0.8"), 1,
                clickedAt.plusSeconds(2));
        createEvent(customer, productB, RecommendationType.POPULAR,
                RecommendationEventType.IMPRESSION, null, 2, clickedAt);
        Order order = createOrder(customer, productA, "DELIVERED", "UNPAID",
                clickedAt.plusMinutes(1));
        completeCodPayment(order);
        entityManager.flush();

        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImpressions").value(2))
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$.totalViews").value(1))
                .andExpect(jsonPath("$.ctr").value(0.5))
                .andExpect(jsonPath("$.totalAddToCarts").value(1))
                .andExpect(jsonPath("$.totalConversions").value(1))
                .andExpect(jsonPath("$.conversionRate").value(1.0));

        RecommendationEvent purchase = findPurchase(order.getId(), productA.getId());
        assertNotNull(purchase);
        assertEquals(click.getId(), purchase.getAttributedFrom().getId());
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void cancelledAndUnpaidOrdersAreNotConversionsEvenIfAnInvalidPurchaseRowExists()
            throws Exception {
        User customer = createCustomer("tracking-invalid-conversions@example.test");
        Product cancelledProduct = createProduct("cancelled-conversion");
        Product unpaidProduct = createProduct("unpaid-conversion");
        LocalDateTime clickTime = LocalDateTime.now().minusMinutes(10);
        RecommendationEvent cancelledClick = createEvent(customer, cancelledProduct,
                RecommendationType.RELATED, RecommendationEventType.CLICK,
                null, 1, clickTime);
        RecommendationEvent unpaidClick = createEvent(customer, unpaidProduct,
                RecommendationType.POPULAR, RecommendationEventType.CLICK,
                null, 1, clickTime.plusSeconds(1));

        Order cancelled = createOrder(customer, cancelledProduct, "CANCELLED", "PAID",
                clickTime.plusMinutes(1));
        Payment paidPayment = createPayment(cancelled, customer, "PAID");
        RecommendationEvent invalidCancelledPurchase = purchaseFixture(
                cancelled, cancelledProduct, cancelledClick, customer, paidPayment.getPaidAt());
        entityManager.persist(invalidCancelledPurchase);

        Order unpaid = createOrder(customer, unpaidProduct, "PENDING", "UNPAID",
                clickTime.plusMinutes(2));
        createPayment(unpaid, customer, "PENDING");
        RecommendationEvent invalidUnpaidPurchase = purchaseFixture(
                unpaid, unpaidProduct, unpaidClick, customer, LocalDateTime.now());
        entityManager.persist(invalidUnpaidPurchase);
        entityManager.flush();

        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.totalConversions").value(0))
                .andExpect(jsonPath("$.conversionRate").value(0.0));
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void typesAndProductsRemainSeparatedAndTypeFilterIsApplied() throws Exception {
        User customer = createCustomer("tracking-type-metrics@example.test");
        Product relatedProduct = createProduct("type-related");
        Product personalizedProduct = createProduct("type-personalized");
        Product popularProduct = createProduct("type-popular");
        LocalDateTime eventTime = LocalDateTime.now().minusMinutes(2);
        createEvent(customer, relatedProduct, RecommendationType.RELATED,
                RecommendationEventType.IMPRESSION, null, 1, eventTime);
        createEvent(customer, relatedProduct, RecommendationType.RELATED,
                RecommendationEventType.CLICK, null, 1, eventTime.plusSeconds(1));
        createEvent(customer, personalizedProduct, RecommendationType.PERSONALIZED,
                RecommendationEventType.IMPRESSION, null, 1, eventTime);
        createEvent(customer, popularProduct, RecommendationType.POPULAR,
                RecommendationEventType.IMPRESSION, null, 1, eventTime);
        entityManager.flush();

        mockMvc.perform(get("/api/analytics/recommendations/types")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recommendationType").value("RELATED"))
                .andExpect(jsonPath("$[0].impressions").value(1))
                .andExpect(jsonPath("$[0].clicks").value(1))
                .andExpect(jsonPath("$[1].recommendationType").value("PERSONALIZED"))
                .andExpect(jsonPath("$[1].impressions").value(1))
                .andExpect(jsonPath("$[2].recommendationType").value("POPULAR"))
                .andExpect(jsonPath("$[2].impressions").value(1));
        mockMvc.perform(get("/api/analytics/recommendations/products")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .param("recommendationType", "RELATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId")
                        .value(org.hamcrest.Matchers.hasItem(relatedProduct.getId().toString())))
                .andExpect(jsonPath("$[*].productId")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                personalizedProduct.getId().toString()))));
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void zeroImpressionsAndZeroClicksProduceZeroRates() throws Exception {
        User customer = createCustomer("tracking-zero-rates@example.test");
        Product product = createProduct("zero-rates");
        createEvent(customer, product, RecommendationType.RELATED,
                RecommendationEventType.CLICK, null, 1, LocalDateTime.now());
        entityManager.flush();

        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImpressions").value(0))
                .andExpect(jsonPath("$.ctr").value(0.0))
                .andExpect(jsonPath("$.conversionRate").value(0.0));
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void productAnalyticsUseDatabaseAggregatesAndReturnOnlyAggregateFields() throws Exception {
        User customer = createCustomer("tracking-product-aggregate@example.test");
        Product product = createProduct("product-aggregate");
        LocalDateTime eventTime = LocalDateTime.now().minusMinutes(2);
        createEvent(customer, product, RecommendationType.RELATED,
                RecommendationEventType.IMPRESSION, null, 1, eventTime);
        createEvent(customer, product, RecommendationType.RELATED,
                RecommendationEventType.CLICK, null, 1, eventTime.plusSeconds(1));
        createEvent(customer, product, RecommendationType.RELATED,
                RecommendationEventType.VIEW, null, 1, eventTime.plusSeconds(2));
        entityManager.flush();

        mockMvc.perform(get("/api/analytics/recommendations/products")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$[0].productName").value(product.getName()))
                .andExpect(jsonPath("$[0].impressions").value(1))
                .andExpect(jsonPath("$[0].clicks").value(1))
                .andExpect(jsonPath("$[0].views").value(1))
                .andExpect(jsonPath("$[0].ctr").value(1.0))
                .andExpect(jsonPath("$[0].conversionRate").value(0.0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("email"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("password"))));
    }

    @Test
    @WithMockUser(username = "tracking-admin@example.test", roles = "ADMIN")
    void clickAfterOrderCreationAndClickByAnotherUserAreNotAttributed() throws Exception {
        User owner = createCustomer("tracking-order-owner@example.test");
        User other = createCustomer("tracking-other-user@example.test");
        Product product = createProduct("no-attribution");
        LocalDateTime orderCreatedAt = LocalDateTime.now().minusMinutes(2);
        createEvent(owner, product, RecommendationType.RELATED,
                RecommendationEventType.CLICK, null, 1, orderCreatedAt.plusSeconds(1));
        createEvent(other, product, RecommendationType.POPULAR,
                RecommendationEventType.CLICK, null, 1, orderCreatedAt.minusSeconds(1));
        Order order = createOrder(owner, product, "DELIVERED", "UNPAID", orderCreatedAt);
        completeCodPayment(order);
        entityManager.flush();

        mockMvc.perform(get("/api/analytics/recommendations/overview")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConversions").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions postEvent(
            CreateRecommendationEventRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/recommendations/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)));
    }

    private String toJson(CreateRecommendationEventRequest request) {
        StringBuilder json = new StringBuilder("{\"recommendationType\":\"")
                .append(request.recommendationType())
                .append("\",\"productId\":\"").append(request.productId())
                .append("\",\"eventType\":\"").append(request.eventType()).append('"');
        if (request.recommendationScore() != null) {
            json.append(",\"recommendationScore\":")
                    .append(request.recommendationScore().toPlainString());
        }
        if (request.position() != null) {
            json.append(",\"position\":").append(request.position());
        }
        if (request.sessionId() != null) {
            json.append(",\"sessionId\":\"").append(request.sessionId()).append('"');
        }
        if (request.clientEventId() != null) {
            json.append(",\"clientEventId\":\"").append(request.clientEventId()).append('"');
        }
        return json.append('}').toString();
    }

    private CreateRecommendationEventRequest request(
            RecommendationType recommendationType,
            Product product,
            RecommendationEventType eventType,
            BigDecimal score,
            Integer position,
            UUID sessionId,
            UUID clientEventId
    ) {
        return new CreateRecommendationEventRequest(
                recommendationType, product.getId(), eventType, score, position,
                sessionId, clientEventId);
    }

    private User createCustomer(String email) {
        User user = User.builder()
                .fullName("Tracking test customer")
                .email(email + "." + UUID.randomUUID())
                .password("not-a-real-password-hash")
                .status("ACTIVE")
                .build();
        if (CUSTOMER_EMAIL.equals(email)) {
            user.setEmail(email);
        }
        entityManager.persist(user);
        return user;
    }

    private Product createProduct(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Product product = Product.builder()
                .name("Recommendation tracking " + label)
                .slug("recommendation-tracking-" + label + "-" + suffix)
                .sku("RTE-" + suffix.substring(0, 20).toUpperCase())
                .price(new BigDecimal("100.00"))
                .quantity(10)
                .status("ACTIVE")
                .build();
        entityManager.persist(product);
        return product;
    }

    private RecommendationEvent createEvent(
            User user,
            Product product,
            RecommendationType recommendationType,
            RecommendationEventType eventType,
            BigDecimal score,
            Integer position,
            LocalDateTime createdAt
    ) {
        RecommendationEvent event = RecommendationEvent.builder()
                .user(user)
                .product(product)
                .recommendationType(recommendationType)
                .eventType(eventType)
                .recommendationScore(score)
                .recommendationPosition(position)
                .createdAt(createdAt)
                .build();
        entityManager.persist(event);
        return event;
    }

    private Order createOrder(
            User user,
            Product product,
            String status,
            String paymentStatus,
            LocalDateTime createdAt
    ) {
        Order order = Order.builder()
                .user(user)
                .orderCode("TRK-" + UUID.randomUUID())
                .subtotal(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .shippingRecipientName("Tracking test")
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
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("100.00"))
                .build());
        entityManager.persist(order);
        entityManager.flush();
        return order;
    }

    private Payment createPayment(Order order, User user, String status) {
        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .paymentMethod("COD")
                .provider("COD")
                .amount(order.getTotalAmount())
                .currency("VND")
                .status(status)
                .idempotencyKey("COD-ORDER-" + order.getId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .paidAt("PAID".equals(status) ? LocalDateTime.now() : null)
                .build();
        entityManager.persist(payment);
        entityManager.flush();
        return payment;
    }

    private RecommendationEvent purchaseFixture(
            Order order,
            Product product,
            RecommendationEvent attributedFrom,
            User user,
            LocalDateTime createdAt
    ) {
        return RecommendationEvent.builder()
                .user(user)
                .sessionId(attributedFrom.getSessionId())
                .product(product)
                .recommendationType(attributedFrom.getRecommendationType())
                .eventType(RecommendationEventType.PURCHASE)
                .recommendationScore(attributedFrom.getRecommendationScore())
                .recommendationPosition(attributedFrom.getRecommendationPosition())
                .order(order)
                .attributedFrom(attributedFrom)
                .createdAt(createdAt)
                .build();
    }

    private void completeCodPayment(Order order) {
        paymentService.recordCodPayment(order);
        paymentService.completeCodPayment(order);
    }

    private RecommendationEvent findPurchase(UUID orderId, UUID productId) {
        return entityManager.createQuery("""
                        select event from RecommendationEvent event
                        where event.order.id = :orderId and event.product.id = :productId
                          and event.eventType = :eventType
                        """, RecommendationEvent.class)
                .setParameter("orderId", orderId)
                .setParameter("productId", productId)
                .setParameter("eventType", RecommendationEventType.PURCHASE)
                .getResultStream().findFirst().orElse(null);
    }

    private long countEvents() {
        return entityManager.createQuery(
                        "select count(event) from RecommendationEvent event", Long.class)
                .getSingleResult();
    }
}
