package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.datamining.CoOccurrenceResponse;
import com.smart_ecommerce.backend.dto.datamining.FrequentItemResponse;
import com.smart_ecommerce.backend.dto.datamining.RfmCustomerResponse;
import com.smart_ecommerce.backend.service.DataMiningService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-mining")
@RequiredArgsConstructor
public class DataMiningController {

    private final DataMiningService dataMiningService;

    @GetMapping("/rfm")
    public ResponseEntity<Page<RfmCustomerResponse>> getRfmCustomers(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(dataMiningService.getRfmCustomers(from, to, page, size));
    }

    @GetMapping("/frequent-items")
    public ResponseEntity<List<FrequentItemResponse>> getFrequentItems(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0.1") BigDecimal minSupport,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(
                dataMiningService.getFrequentItems(from, to, minSupport, limit));
    }

    @GetMapping("/co-occurrence")
    public ResponseEntity<List<CoOccurrenceResponse>> getCoOccurrences(
            @RequestParam UUID productId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0.1") BigDecimal minSupport,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(dataMiningService.getCoOccurrences(
                productId, from, to, minSupport, limit));
    }
}
