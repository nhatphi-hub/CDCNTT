package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.datamining.CoOccurrenceResponse;
import com.smart_ecommerce.backend.dto.datamining.FrequentItemResponse;
import com.smart_ecommerce.backend.dto.datamining.RfmCustomerResponse;
import com.smart_ecommerce.backend.repository.DataMiningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataMiningService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_LIMIT = 100;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final DataMiningRepository dataMiningRepository;

    public Page<RfmCustomerResponse> getRfmCustomers(
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        DateRange range = resolveRange(from, to);
        validatePage(page, size);
        long requestedOffset = (long) page * size;
        if (requestedOffset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "page vượt quá phạm vi được hỗ trợ");
        }

        long totalCustomers = dataMiningRepository.countRfmCustomers(
                range.startAt(), range.endExclusive());
        List<RfmCustomerResponse> content = dataMiningRepository.findRfmCustomers(
                range.startAt(), range.endExclusive(), range.referenceDate(),
                (int) requestedOffset, size);
        return new PageImpl<>(content, PageRequest.of(page, size), totalCustomers);
    }

    public List<FrequentItemResponse> getFrequentItems(
            LocalDate from,
            LocalDate to,
            BigDecimal minSupport,
            Integer requestedLimit
    ) {
        DateRange range = resolveRange(from, to);
        BigDecimal supportThreshold = validateMinSupport(minSupport);
        int limit = validateLimit(requestedLimit);
        long transactionCount = dataMiningRepository.countEligibleTransactions(
                range.startAt(), range.endExclusive());
        if (transactionCount == 0) {
            return List.of();
        }

        long minimumCount = supportThreshold
                .multiply(BigDecimal.valueOf(transactionCount))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        return dataMiningRepository.findFrequentPairs(
                        range.startAt(), range.endExclusive(), minimumCount, limit)
                .stream()
                .map(pair -> new FrequentItemResponse(
                        pair.productId1(), pair.productName1(),
                        pair.productId2(), pair.productName2(), pair.transactionCount(),
                        DataMiningRepository.ratio(pair.transactionCount(), transactionCount)))
                .toList();
    }

    public List<CoOccurrenceResponse> getCoOccurrences(
            UUID productId,
            LocalDate from,
            LocalDate to,
            BigDecimal minSupport,
            Integer requestedLimit
    ) {
        DateRange range = resolveRange(from, to);
        BigDecimal supportThreshold = validateMinSupport(minSupport);
        int limit = validateLimit(requestedLimit);
        long transactionCount = dataMiningRepository.countEligibleTransactions(
                range.startAt(), range.endExclusive());
        long productTransactionCount = dataMiningRepository.countOrdersContainingProduct(
                productId, range.startAt(), range.endExclusive());
        if (transactionCount == 0 || productTransactionCount == 0) {
            return List.of();
        }

        long minimumCount = supportThreshold
                .multiply(BigDecimal.valueOf(transactionCount))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        return dataMiningRepository.findCoOccurrences(
                        productId, range.startAt(), range.endExclusive(), limit)
                .stream()
                .filter(aggregate -> aggregate.transactionCount() >= minimumCount)
                .map(aggregate -> new CoOccurrenceResponse(
                        aggregate.productId(), aggregate.productName(), aggregate.transactionCount(),
                        DataMiningRepository.ratio(aggregate.transactionCount(), transactionCount),
                        DataMiningRepository.ratio(
                                aggregate.transactionCount(), productTransactionCount)))
                .toList();
    }

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.withDayOfMonth(1) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "from phải nhỏ hơn hoặc bằng to");
        }
        if (resolvedTo.equals(LocalDate.MAX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "to vượt quá phạm vi ngày được hỗ trợ");
        }
        return new DateRange(
                resolvedFrom.atStartOfDay(),
                resolvedTo.plusDays(1).atStartOfDay(),
                resolvedTo);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page phải >= 0 và size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? 10 : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit phải từ 1 đến " + MAX_LIMIT);
        }
        return limit;
    }

    private BigDecimal validateMinSupport(BigDecimal minSupport) {
        BigDecimal support = minSupport == null ? new BigDecimal("0.1") : minSupport;
        if (support.compareTo(ZERO) <= 0 || support.compareTo(ONE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "minSupport phải lớn hơn 0 và không vượt quá 1");
        }
        return support;
    }

    private record DateRange(
            LocalDateTime startAt,
            LocalDateTime endExclusive,
            LocalDate referenceDate
    ) {
    }
}
