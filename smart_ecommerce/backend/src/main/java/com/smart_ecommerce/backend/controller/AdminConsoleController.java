package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.seller.ShopResponse;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.Payment;
import com.smart_ecommerce.backend.repository.OrderRepository;
import com.smart_ecommerce.backend.repository.PaymentRepository;
import com.smart_ecommerce.backend.repository.ShopRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** ADMIN-only, read-only overview. Never updates payment statuses or refunds. */
@RestController
@RequestMapping("/api/admin/console")
@Transactional(readOnly = true)
public class AdminConsoleController {
    private final ShopRepository shops;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public AdminConsoleController(ShopRepository shops, OrderRepository orders, PaymentRepository payments) {
        this.shops = shops;
        this.orders = orders;
        this.payments = payments;
    }

    private PageRequest page(int index, int size) {
        if (index < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "page >= 0 và size trong khoảng 1..50");
        }
        return PageRequest.of(index, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String filter(String value, Set<String> accepted) {
        String status = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!status.isEmpty() && !accepted.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        }
        return status;
    }

    @GetMapping("/shops")
    public Page<ShopResponse> shops(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String s = filter(status, Set.of("PENDING", "ACTIVE", "SUSPENDED", "REJECTED"));
        return (s.isEmpty() ? shops.findAll(page(page,size)) :
            shops.findByStatusIgnoreCase(s,page(page,size))).map(ShopResponse::from);
    }

    @GetMapping("/orders")
    public Page<OrderRow> orders(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String s = filter(status, Set.of("PENDING", "CONFIRMED", "SHIPPING", "DELIVERED", "CANCELLED"));
        return (s.isEmpty() ? orders.findAll(page(page,size)) :
            orders.findByStatusIgnoreCase(s,page(page,size))).map(o ->
            new OrderRow(o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentStatus(),
                o.getPaymentMethod(), o.getTotalAmount(), o.getCreatedAt()));
    }

    @GetMapping("/payments")
    public Page<PaymentRow> payments(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String s = filter(status, Set.of("PENDING", "PAID", "FAILED", "CANCELLED", "EXPIRED", "REFUNDED"));
        return (s.isEmpty() ? payments.findAll(page(page,size)) :
            payments.findByStatusIgnoreCase(s,page(page,size))).map(p ->
            new PaymentRow(p.getId(), p.getOrder().getId(), p.getOrder().getOrderCode(),
                p.getPaymentMethod(), p.getProvider(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getProviderTransactionId(), p.getFailureCode(),
                p.getCreatedAt(), p.getPaidAt()));
    }

    public record OrderRow(UUID id, String orderCode, String status, String paymentStatus,
                           String paymentMethod, BigDecimal totalAmount, LocalDateTime createdAt) {}
    /** Does not expose idempotency key, customer email or provider credential. */
    public record PaymentRow(UUID id, UUID orderId, String orderCode, String paymentMethod,
                             String provider, BigDecimal amount, String currency, String status,
                             String providerTransactionId, String failureCode,
                             LocalDateTime createdAt, LocalDateTime paidAt) {}
}
