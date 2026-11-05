package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.payment.CreatePaymentRequest;
import com.smart_ecommerce.backend.dto.payment.PaymentCallbackRequest;
import com.smart_ecommerce.backend.dto.payment.PaymentResponse;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.Payment;
import com.smart_ecommerce.backend.repository.OrderRepository;
import com.smart_ecommerce.backend.repository.PaymentRepository;
import com.smart_ecommerce.backend.service.payment.PaymentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private static final Set<String> PAYMENT_METHODS =
            Set.of("COD", "BANK_TRANSFER", "VNPAY", "MOMO");
    private static final Set<String> ACTIVE_PAYMENT_STATUSES =
            Set.of("PENDING", "PROCESSING");
    private static final Set<String> TERMINAL_PAYMENT_STATUSES =
            Set.of("PAID", "FAILED", "CANCELLED", "EXPIRED", "REFUNDED");

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final List<PaymentProvider> paymentProviders;
    private final RecommendationTrackingService recommendationTrackingService;

    public PaymentResponse createPayment(
            UUID userId,
            CreatePaymentRequest request,
            String suppliedIdempotencyKey
    ) {
        String idempotencyKey = normalizeIdempotencyKey(suppliedIdempotencyKey);
        String requestedMethod = normalizePaymentMethod(request.paymentMethod());
        if (!PAYMENT_METHODS.contains(requestedMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Phương thức thanh toán không hợp lệ");
        }

        Order order = orderRepository.findByIdAndUserIdForUpdate(request.orderId(), userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));

        Payment previousRequest = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (previousRequest != null) {
            if (!request.orderId().equals(previousRequest.getOrder().getId())
                    || !userId.equals(previousRequest.getUser().getId())
                    || !requestedMethod.equals(previousRequest.getPaymentMethod())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key đã được dùng cho một yêu cầu thanh toán khác");
            }
            return toResponse(previousRequest);
        }

        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán");
        }
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể tạo thanh toán cho đơn hàng đang ở trạng thái PENDING");
        }
        if (!"UNPAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Trạng thái thanh toán của đơn hàng không phù hợp");
        }
        String orderMethod = normalizePaymentMethod(order.getPaymentMethod());
        if (!requestedMethod.equals(orderMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentMethod phải trùng với phương thức đã chọn khi tạo đơn hàng");
        }
        if ("COD".equals(requestedMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "COD không tạo payment gateway; payment COD được ghi nhận cùng đơn hàng và hoàn tất khi giao thành công");
        }
        BigDecimal amount = order.getTotalAmount();
        if (amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đơn hàng có tổng tiền không hợp lệ");
        }
        if (!"COD".equals(requestedMethod) && amount.signum() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng có tổng tiền bằng 0 nên không thể tạo payment gateway");
        }

        paymentRepository.findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(
                        order.getId(), ACTIVE_PAYMENT_STATUSES)
                .ifPresent(activePayment -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Đơn hàng đã có payment attempt đang chờ xử lý: "
                                    + activePayment.getId());
                });

        Payment payment = Payment.builder()
                .order(order)
                .user(order.getUser())
                .paymentMethod(requestedMethod)
                .provider(findProvider(requestedMethod).providerCode())
                .amount(amount)
                .currency("VND")
                .status("PENDING")
                .idempotencyKey(idempotencyKey)
                .build();

        findProvider(requestedMethod).initialize(payment);

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, UUID userId, boolean admin) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy payment"));
        ensurePaymentVisible(payment, userId, admin);
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrder(UUID orderId, UUID userId, boolean admin) {
        if (admin) {
            if (!orderRepository.existsById(orderId)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng");
            }
        } else {
            orderRepository.findByIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        }
        return paymentRepository.findByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PaymentResponse handleCallback(
            String providerCode,
            PaymentCallbackRequest request,
            String signature
    ) {
        String normalizedProvider = providerCode == null
                ? ""
                : providerCode.trim().toUpperCase(Locale.ROOT);
        PaymentProvider provider = paymentProviders.stream()
                .filter(candidate -> candidate.providerCode().equalsIgnoreCase(normalizedProvider))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Payment provider không được hỗ trợ"));

        if (!provider.verifyCallback(request, signature)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Callback signature không hợp lệ");
        }

        Payment initialPayment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy payment"));
        UUID orderId = initialPayment.getOrder().getId();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        Payment payment = paymentRepository.findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy payment"));
        if (!orderId.equals(payment.getOrder().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment đã thay đổi liên kết đơn hàng trong lúc xử lý callback");
        }
        if (!normalizedProvider.equalsIgnoreCase(payment.getProvider())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Provider callback không khớp payment");
        }
        if (!provider.supportsPaymentMethod(payment.getPaymentMethod())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment method này không nhận callback gateway");
        }
        validateCallbackAmountAndCurrency(payment, request);

        String callbackStatus = request.status().trim().toUpperCase(Locale.ROOT);
        String transactionId = request.providerTransactionId().trim();
        if (TERMINAL_PAYMENT_STATUSES.contains(normalizePaymentMethod(payment.getStatus()))) {
            if (callbackStatus.equals(payment.getStatus())
                    && transactionId.equals(payment.getProviderTransactionId())) {
                return toResponse(payment);
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment attempt đã ở trạng thái kết thúc");
        }

        if (!ACTIVE_PAYMENT_STATUSES.contains(normalizePaymentMethod(payment.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment attempt không thể nhận callback ở trạng thái hiện tại");
        }
        if (paymentRepository.existsByProviderAndProviderTransactionIdAndIdNot(
                normalizedProvider, transactionId, payment.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "providerTransactionId đã được dùng cho payment khác");
        }

        if (!order.getUser().getId().equals(payment.getUser().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment user không khớp chủ sở hữu đơn hàng");
        }
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán bằng payment attempt khác");
        }
        if (!"UNPAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Trạng thái thanh toán của đơn hàng không phù hợp");
        }

        if ("PAID".equals(callbackStatus)) {
            if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Không thể xác nhận payment cho đơn hàng đã bị hủy");
            }
            payment.setStatus("PAID");
            payment.setPaidAt(LocalDateTime.now());
            payment.setFailureCode(null);
            payment.setFailureMessage(null);
            order.setPaymentStatus("PAID");
            recommendationTrackingService.recordPurchaseConversions(order, payment);
        } else if ("FAILED".equals(callbackStatus)) {
            payment.setStatus("FAILED");
            payment.setFailureCode("PROVIDER_FAILED");
            payment.setFailureMessage("Nhà cung cấp xác nhận giao dịch thất bại");
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Callback status chỉ được là PAID hoặc FAILED");
        }

        payment.setProviderTransactionId(transactionId);
        return toResponse(payment);
    }

    /** Creates the non-gateway COD record as part of the order transaction. */
    public void recordCodPayment(Order order) {
        if (!"COD".equalsIgnoreCase(normalizePaymentMethod(order.getPaymentMethod()))) {
            return;
        }
        if (order.getId() == null || order.getUser() == null || order.getTotalAmount() == null) {
            throw new IllegalArgumentException("Đơn hàng COD chưa được lưu hoặc thiếu dữ liệu thanh toán");
        }

        Payment existing = paymentRepository
                .findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(order.getId(), "COD")
                .orElse(null);
        if (existing != null) {
            ensureCodPaymentMatchesOrder(existing, order);
            return;
        }

        Payment payment = Payment.builder()
                .order(order)
                .user(order.getUser())
                .paymentMethod("COD")
                .provider("COD")
                .amount(order.getTotalAmount())
                .currency("VND")
                .status("PENDING")
                .idempotencyKey("COD-ORDER-" + order.getId())
                .build();
        paymentRepository.save(payment);
    }

    /** Cancels pending attempts while the caller holds the owning order lock. */
    public void cancelPendingPayments(Order order) {
        List<Payment> pendingPayments = paymentRepository.findByOrder_IdAndStatusIn(
                order.getId(), ACTIVE_PAYMENT_STATUSES);
        for (Payment payment : pendingPayments) {
            payment.setStatus("CANCELLED");
            payment.setFailureCode("ORDER_CANCELLED");
            payment.setFailureMessage("Đơn hàng đã bị hủy");
        }
    }

    /** Marks COD collected only when delivery has succeeded. */
    public void completeCodPayment(Order order) {
        if (!"COD".equalsIgnoreCase(normalizePaymentMethod(order.getPaymentMethod()))) {
            if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Đơn thanh toán trực tuyến phải được thanh toán trước khi giao thành công");
            }
            return;
        }

        recordCodPayment(order);
        Payment payment = paymentRepository
                .findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(order.getId(), "COD")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không thể tạo bản ghi thanh toán COD"));
        ensureCodPaymentMatchesOrder(payment, order);
        if ("PAID".equalsIgnoreCase(payment.getStatus())) {
            order.setPaymentStatus("PAID");
            recommendationTrackingService.recordPurchaseConversions(order, payment);
            return;
        }
        if (!ACTIVE_PAYMENT_STATUSES.contains(normalizePaymentMethod(payment.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment COD không ở trạng thái có thể hoàn tất");
        }

        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        payment.setFailureCode(null);
        payment.setFailureMessage(null);
        order.setPaymentStatus("PAID");
        recommendationTrackingService.recordPurchaseConversions(order, payment);
    }

    private void ensureCodPaymentMatchesOrder(Payment payment, Order order) {
        if (!"COD".equalsIgnoreCase(payment.getPaymentMethod())
                || !"COD".equalsIgnoreCase(payment.getProvider())
                || !order.getId().equals(payment.getOrder().getId())
                || !order.getUser().getId().equals(payment.getUser().getId())
                || payment.getAmount() == null
                || payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment COD không khớp thông tin đơn hàng");
        }
    }

    private PaymentProvider findProvider(String paymentMethod) {
        return paymentProviders.stream()
                .filter(provider -> provider.supportsPaymentMethod(paymentMethod))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Chưa cấu hình payment provider cho " + paymentMethod));
    }

    private void ensurePaymentVisible(Payment payment, UUID userId, boolean admin) {
        if (!admin && !payment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy payment");
        }
    }

    private void validateCallbackAmountAndCurrency(
            Payment payment,
            PaymentCallbackRequest request
    ) {
        if (request.amount() == null || payment.getAmount().compareTo(request.amount()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Callback amount không khớp payment amount");
        }
        if (request.currency() == null
                || !payment.getCurrency().equalsIgnoreCase(request.currency().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Callback currency không khớp payment currency");
        }
        if (request.providerTransactionId() == null
                || request.providerTransactionId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "providerTransactionId không hợp lệ");
        }
    }

    private String normalizeIdempotencyKey(String suppliedKey) {
        String key = suppliedKey == null ? "" : suppliedKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key là bắt buộc và tối đa 100 ký tự");
        }
        return key;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        return paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
    }

    private PaymentResponse toResponse(Payment payment) {
        Order order = payment.getOrder();
        return new PaymentResponse(
                payment.getId(),
                order.getId(),
                order.getOrderCode(),
                payment.getUser().getId(),
                payment.getPaymentMethod(),
                payment.getProvider(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProviderTransactionId(),
                payment.getIdempotencyKey(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getPaidAt(),
                payment.getExpiredAt()
        );
    }
}
