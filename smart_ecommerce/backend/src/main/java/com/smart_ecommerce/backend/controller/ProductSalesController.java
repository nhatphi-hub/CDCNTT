package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.repository.AnalyticsRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Public, aggregated sales totals for visible products, without customer data. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductSalesController {
    private final AnalyticsRepository analyticsRepository;
    private final ProductRepository productRepository;

    @GetMapping("/sales-counts")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> salesCounts() {
        var ids = productRepository.findVisibleCatalog().stream().map(p -> p.getId()).toList();
        var aggregates = analyticsRepository.productSales(ids,
                LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.now().plusDays(1));
        var response = new LinkedHashMap<String, Long>();
        for (UUID id : ids) {
            var stats = aggregates.get(id);
            response.put(id.toString(), stats == null ? 0L : stats.quantitySold());
        }
        return ResponseEntity.ok(response);
    }
}
