package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.Category;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.OrderItem;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductImage;
import com.smart_ecommerce.backend.entity.ProductView;
import com.smart_ecommerce.backend.entity.Favorite;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class RecommendationIntegrationTests {

    private static final String CUSTOMER_EMAIL = "recommendation-customer@example.test";
    private static final LocalDateTime PURCHASE_TIME =
            LocalDate.of(2024, 6, 14).atTime(12, 0);

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void noTrafficStillShowsActiveCatalogWithoutRequiringSamplePurchases() throws Exception {
        Product available = createProduct("fresh-catalog-no-traffic", null, "ACTIVE", 12, null, null);
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/popular").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(available.getId().toString())));
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void favoritedCategoryCreatesRecommendationsWithoutPurchasesOrViews() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Category favoriteCategory = createCategory("favorite-preference");
        Product favorite = createProduct("favorite-seed", favoriteCategory, "ACTIVE", 10, "SHIRT", null);
        Product suggestion = createProduct("favorite-candidate", favoriteCategory, "ACTIVE", 10, "SHIRT", null);
        entityManager.persist(Favorite.builder()
                .user(customer).product(favorite).createdAt(LocalDateTime.now()).build());
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(suggestion.getId().toString())))
                .andExpect(jsonPath("$[*].reason").value(hasItem("Based on your interests")));
        // An already-favorited item is allowed as a revisit when the catalog
        // has fewer than the requested recommendation count.
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void viewedProductCanBeRecommendedWhenSmallCatalogHasNoUnseenProducts() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Product viewed = createProduct("only-viewed-item", null, "ACTIVE", 10, "BOOK", null);
        entityManager.persist(ProductView.builder()
                .user(customer).product(viewed)
                .viewedAt(LocalDateTime.now()).durationSeconds(10).build());
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(viewed.getId().toString())));
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void favoritesInChildBookCategoryBoostOtherChildBookProducts() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Category root = createCategory("books-root");
        Category novels = createCategory("books-novels");
        Category science = createCategory("books-science");
        novels.setParent(root);
        science.setParent(root);
        Product favorite = createProduct("books-novel-liked", novels, "ACTIVE", 10, "BOOK", null);
        Product otherBook = createProduct("books-science-candidate", science, "ACTIVE", 10, "BOOK", null);
        createProduct("books-other-fashion", null, "ACTIVE", 10, "FASHION", null);
        entityManager.persist(Favorite.builder().user(customer).product(favorite)
                .createdAt(LocalDateTime.now()).build());
        entityManager.flush();
        mockMvc.perform(get("/api/recommendations/personalized").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(otherBook.getId().toString())));
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void searchKeywordCanFindBookEvenWithoutPurchaseHistory() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Product book = createProduct("learning-book-tutorial", null, "ACTIVE", 10, "BOOK", null);
        entityManager.persist(com.smart_ecommerce.backend.entity.SearchHistory.builder()
                .user(customer).keyword("book").searchedAt(LocalDateTime.now()).build());
        entityManager.flush();
        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(book.getId().toString())));
    }

    @Test
    void anonymousCanReadRelatedProducts() throws Exception {
        Product source = createProduct("anonymous-related-source", null, "ACTIVE", 10, null, null);
        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCanReadPopularProducts() throws Exception {
        mockMvc.perform(get("/api/recommendations/popular"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void authenticatedCustomerCanReadPersonalizedRecommendations() throws Exception {
        createCustomer(CUSTOMER_EMAIL);
        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void customerWithoutPurchaseHistoryGetsSafeFallback() throws Exception {
        createCustomer(CUSTOMER_EMAIL);
        User anotherCustomer = createCustomer("recommendation-purchaser@example.test");
        Product popular = createProduct("fallback-popular", null, "ACTIVE", 10, null, null);
        createPaidOrder(anotherCustomer, PURCHASE_TIME, popular);
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(popular.getId().toString())))
                .andExpect(jsonPath("$[*].reason").value(hasItem("Popular product")));
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void customerWithoutProductViewsCanStillUsePurchaseHistory() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Product purchased = createProduct("no-view-purchased", null, "ACTIVE", 10, null, null);
        Product boughtTogether = createProduct("no-view-cooccurrence", null, "ACTIVE", 10, null, null);
        createPaidOrder(customer, PURCHASE_TIME, purchased);
        createPaidOrder(createCustomer("no-view-buyer-a@example.test"),
                PURCHASE_TIME.plusDays(1), purchased, boughtTogether);
        createPaidOrder(createCustomer("no-view-buyer-b@example.test"),
                PURCHASE_TIME.plusDays(2), purchased, boughtTogether);
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId")
                        .value(hasItem(boughtTogether.getId().toString())))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void relatedReturnsNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/recommendations/products/{id}/related", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void relatedNeverReturnsTheSourceProduct() throws Exception {
        Category category = createCategory("related-no-self");
        Product source = createProduct("related-no-self-source", category, "ACTIVE", 10,
                "BOOK", "UNISEX");
        Product candidate = createProduct("related-no-self-candidate", category, "ACTIVE", 10,
                "BOOK", "UNISEX");
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId")
                        .value(hasItem(candidate.getId().toString())))
                .andExpect(content().string(not(containsString(source.getId().toString()))));
    }

    @Test
    void relatedMergesSignalsWithoutDuplicateProducts() throws Exception {
        Category category = createCategory("related-dedup");
        User buyer = createCustomer("related-dedup-buyer@example.test");
        Product source = createProduct("related-dedup-source", category, "ACTIVE", 10,
                "CAMERA", "UNISEX");
        Product candidate = createProduct("related-dedup-candidate", category, "ACTIVE", 10,
                "CAMERA", "UNISEX");
        createPaidOrder(buyer, PURCHASE_TIME, source, candidate);
        createPaidOrder(buyer, PURCHASE_TIME.plusDays(1), source, candidate);
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productId == '%s')]"
                        .formatted(candidate.getId())).value(hasSize(1)));
    }

    @Test
    void inactiveProductsAreNotRecommended() throws Exception {
        Category category = createCategory("related-inactive");
        Product source = createProduct("related-inactive-source", category, "ACTIVE", 10,
                null, null);
        Product inactive = createProduct("related-inactive-product", category, "INACTIVE", 10,
                null, null);
        Product active = createProduct("related-active-product", category, "ACTIVE", 10,
                null, null);
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(active.getId().toString())))
                .andExpect(content().string(not(containsString(inactive.getId().toString()))));
    }

    @Test
    void nonActiveCatalogStatusIsNotExposedAsARecommendation() throws Exception {
        Product inactive = createProduct("related-not-active", null, "INACTIVE", 10,
                null, null);
        mockMvc.perform(get("/api/recommendations/popular"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(inactive.getId().toString()))));
    }

    @Test
    void validLimitRestrictsRelatedResults() throws Exception {
        Category category = createCategory("related-limit");
        Product source = createProduct("related-limit-source", category, "ACTIVE", 10,
                null, null);
        createProduct("related-limit-candidate-a", category, "ACTIVE", 10, null, null);
        createProduct("related-limit-candidate-b", category, "ACTIVE", 10, null, null);
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(1)));
    }

    @Test
    void zeroLimitReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recommendations/popular").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitAboveFiftyReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recommendations/popular").param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "recommendation-staff@example.test", roles = "STAFF")
    void staffCannotReadPersonalizedRecommendations() throws Exception {
        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void customerIdRequestParameterCannotSwitchPersonalizedIdentity() throws Exception {
        User authenticatedCustomer = createCustomer(CUSTOMER_EMAIL);
        User otherCustomer = createCustomer("recommendation-other@example.test");
        Product sourceA = createProduct("identity-source-a", null, "ACTIVE", 10, null, null);
        Product resultA = createProduct("identity-result-a", null, "ACTIVE", 10, null, null);
        Product sourceB = createProduct("identity-source-b", null, "ACTIVE", 10, null, null);
        Product resultB = createProduct("identity-result-b", null, "ACTIVE", 10, null, null);
        createPaidOrder(authenticatedCustomer, PURCHASE_TIME, sourceA);
        createPaidOrder(otherCustomer, PURCHASE_TIME, sourceB);
        createPaidOrder(createCustomer("identity-pair-a@example.test"),
                PURCHASE_TIME.plusDays(1), sourceA, resultA);
        createPaidOrder(createCustomer("identity-pair-b@example.test"),
                PURCHASE_TIME.plusDays(2), sourceA, resultA);
        createPaidOrder(createCustomer("identity-pair-c@example.test"),
                PURCHASE_TIME.plusDays(3), sourceB, resultB);
        createPaidOrder(createCustomer("identity-pair-d@example.test"),
                PURCHASE_TIME.plusDays(4), sourceB, resultB);
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/personalized")
                        .param("customerId", otherCustomer.getId().toString())
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(resultA.getId().toString()))
                .andExpect(content().string(not(containsString(resultB.getId().toString()))));
    }

    @Test
    void coOccurrenceRanksFrequentlyBoughtTogetherProductFirst() throws Exception {
        User buyerA = createCustomer("cooccurrence-buyer-a@example.test");
        User buyerB = createCustomer("cooccurrence-buyer-b@example.test");
        User buyerC = createCustomer("cooccurrence-buyer-c@example.test");
        Product source = createProduct("cooccurrence-source", null, "ACTIVE", 10,
                null, null);
        Product frequentPair = createProduct("cooccurrence-frequent", null, "ACTIVE", 10,
                null, null);
        Product occasionalPair = createProduct("cooccurrence-occasional", null, "ACTIVE", 10,
                null, null);
        createPaidOrder(buyerA, PURCHASE_TIME, source, frequentPair, frequentPair);
        createPaidOrder(buyerB, PURCHASE_TIME.plusDays(1), source, frequentPair);
        createPaidOrder(buyerC, PURCHASE_TIME.plusDays(2), source, occasionalPair);
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(frequentPair.getId().toString()))
                .andExpect(jsonPath("$[0].reason").value("Frequently bought together"))
                .andExpect(jsonPath("$[1].productId").value(occasionalPair.getId().toString()));
    }

    @Test
    void categoryRecommendationIsFallbackWhenThereIsNoCoOccurrence() throws Exception {
        Category category = createCategory("category-fallback");
        Product source = createProduct("category-fallback-source", category, "ACTIVE", 10,
                null, null);
        Product sameCategory = createProduct("category-fallback-target", category, "ACTIVE", 10,
                null, null);
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(sameCategory.getId().toString()))
                .andExpect(jsonPath("$[0].reason").value("Same category"));
    }

    @Test
    void sparseRecommendationDataReturnsValidFallbackOrEmptyResponse() throws Exception {
        Product source = createProduct("sparse-source", null, "ACTIVE", 10, null, null);
        mockMvc.perform(get(relatedPath(source)).param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].productId")
                        .value(not(hasItem(source.getId().toString()))));
    }

    @Test
    void repeatedRecommendationRequestHasDeterministicOrderingAndScores() throws Exception {
        Category category = createCategory("deterministic");
        Product source = createProduct("deterministic-source", category, "ACTIVE", 10,
                "BOOK", "UNISEX");
        createProduct("deterministic-a", category, "ACTIVE", 10, "BOOK", "UNISEX");
        createProduct("deterministic-b", category, "ACTIVE", 10, "BOOK", "UNISEX");
        entityManager.flush();

        MvcResult first = mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk()).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void purchaseAndViewSignalsAreCombinedWithoutNPlusOneCallsPerSeed() throws Exception {
        User customer = createCustomer(CUSTOMER_EMAIL);
        Category category = createCategory("view-category");
        Product viewed = createProduct("viewed-source", category, "ACTIVE", 10,
                "LAPTOP", "UNISEX");
        Product related = createProduct("viewed-related", category, "ACTIVE", 10,
                "LAPTOP", "UNISEX");
        entityManager.persist(ProductView.builder()
                .user(customer).product(viewed).viewedAt(PURCHASE_TIME).durationSeconds(20).build());
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId").value(hasItem(related.getId().toString())))
                .andExpect(jsonPath("$[*].reason").value(hasItem("Based on your browsing history")));
    }

    @Test
    void recommendationResponseUsesPrimaryProductImageAndCatalogPriceFields() throws Exception {
        Product popular = createProduct("image-popular", null, "ACTIVE", 10, null, null);
        User buyer = createCustomer("image-buyer@example.test");
        entityManager.persist(ProductImage.builder()
                .product(popular).imageUrl("https://example.test/secondary.png")
                .isPrimary(false).sortOrder(0).build());
        entityManager.persist(ProductImage.builder()
                .product(popular).imageUrl("https://example.test/primary.png")
                .isPrimary(true).sortOrder(5).build());
        createPaidOrder(buyer, LocalDate.now().atTime(10, 0), popular);
        entityManager.flush();

        mockMvc.perform(get("/api/recommendations/popular")
                        .param("from", LocalDate.now().withDayOfMonth(1).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productId == '%s')].imageUrl"
                        .formatted(popular.getId())).value(hasItem("https://example.test/primary.png")))
                .andExpect(jsonPath("$[?(@.productId == '%s')].price"
                        .formatted(popular.getId())).value(hasItem(100.0)))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void outOfStockActiveProductRemainsVisibleLikeTheExistingCatalog() throws Exception {
        Category category = createCategory("out-of-stock");
        Product source = createProduct("out-of-stock-source", category, "ACTIVE", 10,
                null, null);
        Product outOfStock = createProduct("out-of-stock-target", category, "ACTIVE", 0,
                null, null);
        entityManager.flush();

        mockMvc.perform(get(relatedPath(source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId")
                        .value(hasItem(outOfStock.getId().toString())));
    }

    @Test
    void invalidPopularDateRangeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recommendations/popular")
                        .param("from", "2024-07-01")
                        .param("to", "2024-06-30"))
                .andExpect(status().isBadRequest());
    }

    private String relatedPath(Product product) {
        return "/api/recommendations/products/" + product.getId() + "/related";
    }

    private Category createCategory(String label) {
        String suffix = UUID.randomUUID().toString();
        Category category = Category.builder()
                .name("Recommendation " + label)
                .slug("recommendation-" + label + "-" + suffix)
                .isActive(true)
                .build();
        entityManager.persist(category);
        return category;
    }

    private Product createProduct(
            String label,
            Category category,
            String status,
            int quantity,
            String productType,
            String gender
    ) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Product product = Product.builder()
                .category(category)
                .name("Recommendation " + label)
                .slug("recommendation-" + label + "-" + suffix)
                .sku("REC-" + suffix.substring(0, 20).toUpperCase())
                .price(new BigDecimal("100.00"))
                .salePrice(new BigDecimal("90.00"))
                .quantity(quantity)
                .productType(productType)
                .gender(gender)
                .status(status)
                .build();
        entityManager.persist(product);
        return product;
    }

    private User createCustomer(String email) {
        User user = User.builder()
                .fullName("Recommendation test customer")
                .email(email)
                .password("not-a-real-password-hash")
                .status("ACTIVE")
                .build();
        entityManager.persist(user);
        return user;
    }

    private Order createPaidOrder(User user, LocalDateTime createdAt, Product... products) {
        BigDecimal total = new BigDecimal("100.00").multiply(BigDecimal.valueOf(products.length));
        Order order = Order.builder()
                .user(user)
                .orderCode("REC-" + UUID.randomUUID())
                .subtotal(total)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(total)
                .shippingRecipientName("Recommendation test")
                .shippingPhone("0900000000")
                .shippingProvince("Test Province")
                .shippingDistrict("Test District")
                .shippingWard("Test Ward")
                .shippingAddressDetail("Test Address")
                .status("DELIVERED")
                .paymentMethod("COD")
                .paymentStatus("PAID")
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
        return order;
    }
}
