package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.controller.ProductSalesController;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.repository.AnalyticsRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSalesControllerTests {
    @Test
    void aggregatesOnlyProductsVisibleInCatalogAndKeepsZeroSales() {
        ProductRepository products = mock(ProductRepository.class);
        AnalyticsRepository analytics = mock(AnalyticsRepository.class);
        UUID soldId = UUID.randomUUID();
        UUID unsoldId = UUID.randomUUID();
        when(products.findVisibleCatalog()).thenReturn(List.of(
                Product.builder().id(soldId).build(),
                Product.builder().id(unsoldId).build()));
        when(analytics.productSales(eq(List.of(soldId, unsoldId)),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Map.of(soldId,
                        new AnalyticsRepository.ProductSalesAggregate(2, 3)));

        Map<String, Long> result = new ProductSalesController(analytics, products)
                .salesCounts().getBody();
        assertEquals(3L, result.get(soldId.toString()));
        assertEquals(0L, result.get(unsoldId.toString()));
        assertEquals(2, result.size());
    }
}
