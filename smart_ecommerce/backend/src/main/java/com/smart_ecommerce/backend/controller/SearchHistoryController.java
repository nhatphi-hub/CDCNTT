package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.searchhistory.*;
import com.smart_ecommerce.backend.service.SearchHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search-history")
@RequiredArgsConstructor
public class SearchHistoryController {
    private final SearchHistoryService searchHistoryService;

    @PostMapping
    public ResponseEntity<SearchHistoryResponse> create(
            @Valid @RequestBody SearchHistoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(searchHistoryService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SearchHistoryResponse>> getMine() {
        return ResponseEntity.ok(searchHistoryService.getMine());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countMine() {
        return ResponseEntity.ok(searchHistoryService.countMine());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMine(@PathVariable UUID id) {
        searchHistoryService.deleteMine(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearMine() {
        searchHistoryService.clearMine();
        return ResponseEntity.noContent().build();
    }
}
