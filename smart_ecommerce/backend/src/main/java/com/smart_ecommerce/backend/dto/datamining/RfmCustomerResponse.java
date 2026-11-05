package com.smart_ecommerce.backend.dto.datamining;

import java.math.BigDecimal;
import java.util.UUID;

/** Customer purchase metrics only; deliberately excludes account and authentication data. */
public record RfmCustomerResponse(
        UUID customerId,
        int recencyDays,
        long frequency,
        BigDecimal monetary,
        Integer recencyScore,
        Integer frequencyScore,
        Integer monetaryScore,
        String rfmScore,
        String segment
) {
}
