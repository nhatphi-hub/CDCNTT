package com.smart_ecommerce.backend.dto.searchhistory;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Builder @AllArgsConstructor
public class SearchHistoryResponse {
    private UUID id;
    private UUID userId;
    private String keyword;
    private LocalDateTime searchedAt;
}
