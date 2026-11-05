package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.payment.CreatePaymentRequest;
import com.smart_ecommerce.backend.dto.payment.PaymentCallbackRequest;
import com.smart_ecommerce.backend.dto.payment.PaymentResponse;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.Payment;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.AddressRepository;
import com.smart_ecommerce.backend.repository.CartRepository;
import com.smart_ecommerce.backend.repository.CouponRepository;
import com.smart_ecommerce.backend.repository.OrderRepository;
import com.smart_ecommerce.backend.repository.PaymentRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductVariantRepository;
import com.smart_ecommerce.backend.repository.ShippingRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import com.smart_ecommerce.backend.service.CouponService;
import com.smart_ecommerce.backend.service.OrderService;
import com.smart_ecommerce.backend.service.PaymentService;
import com.smart_ecommerce.backend.service.RecommendationTrackingService;
import com.smart_ecommerce.backend.service.payment.MockPaymentProvider;
import com.smart_ecommerce.backend.service.payment.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.smart_ecommerce.backend.repository.ShopOrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTests {

    private static final String CALLBACK_SECRET = "test-only-callback-secret";
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PAYMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private MockPaymentProvider provider;
    private PaymentService paymentService;
    private RecommendationTrackingService recommendationTrackingService;
    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        provider = new MockPaymentProvider(CALLBACK_SECRET);
        recommendationTrackingService = mock(RecommendationTrackingService.class);
        List<PaymentProvider> providers = List.of(provider);
        paymentService = new PaymentService(
                paymentRepository, orderRepository, providers, recommendationTrackingService);

        user = User.builder()
                .id(USER_ID)
                .fullName("Payment Test")
                .email("payment-test@example.test")
                .password("not-a-real-password")
                .status("ACTIVE")
                .build();
        order = Order.builder()
                .id(ORDER_ID)
                .user(user)
                .orderCode("ORD-PAYMENT-TEST")
                .totalAmount(new BigDecimal("125000.00"))
                .status("PENDING")
                .paymentMethod("VNPAY")
                .paymentStatus("UNPAID")
                .build();
    }

    @Test
    void createPaymentUsesOrderTotalAndCreatesPendingAttempt() {
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("create-key-1"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(PAYMENT_ID);
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(payment.getCreatedAt());
            return payment;
        });

        PaymentResponse result = paymentService.createPayment(
                USER_ID,
                new CreatePaymentRequest(ORDER_ID, "VNPAY"),
                "create-key-1");

        assertEquals(new BigDecimal("125000.00"), result.amount());
        assertEquals("PENDING", result.status());
        assertEquals("MOCK", result.provider());
        assertEquals("VNPAY", result.paymentMethod());
        assertEquals("create-key-1", result.idempotencyKey());
    }

    @Test
    void createPaymentRejectsOrderOwnedByAnotherCustomerAsNotFound() {
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(
                        USER_ID, new CreatePaymentRequest(ORDER_ID, "VNPAY"), "key-2"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void repeatedCreateWithSameIdempotencyKeyReturnsTheSameAttempt() {
        Payment existing = payment("PENDING");
        existing.setIdempotencyKey("retry-key");
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("retry-key"))
                .thenReturn(Optional.of(existing));

        PaymentResponse result = paymentService.createPayment(
                USER_ID,
                new CreatePaymentRequest(ORDER_ID, "VNPAY"),
                "retry-key");

        assertEquals(PAYMENT_ID, result.id());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void secondActivePaymentAttemptForTheSameOrderIsRejected() {
        Payment active = payment("PENDING");
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("create-key-2"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(
                ORDER_ID, java.util.Set.of("PENDING", "PROCESSING")))
                .thenReturn(Optional.of(active));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(
                        USER_ID, new CreatePaymentRequest(ORDER_ID, "VNPAY"), "create-key-2"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void codPaymentDoesNotCreateOrCallAGateway() {
        order.setPaymentMethod("COD");
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("cod-key"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(
                        USER_ID, new CreatePaymentRequest(ORDER_ID, "COD"), "cod-key"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void validPaidCallbackUpdatesAttemptAndOrderWithoutTouchingInventory() {
        Payment payment = payment("PENDING");
        PaymentCallbackRequest callback = callback("125000.00", "txn-success-1", "PAID");
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByProviderAndProviderTransactionIdAndIdNot(
                "MOCK", "txn-success-1", PAYMENT_ID)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse result = paymentService.handleCallback(
                "MOCK", callback, MockPaymentProvider.signCallback(CALLBACK_SECRET, callback));

        assertEquals("PAID", result.status());
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("PENDING", order.getStatus());
        assertNotNull(payment.getPaidAt());
    }

    @Test
    void validPaidCallbackDoesNotUndoExistingOrderProgress() {
        order.setStatus("CONFIRMED");
        Payment payment = payment("PENDING");
        PaymentCallbackRequest callback = callback("125000.00", "txn-confirmed-order", "PAID");
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByProviderAndProviderTransactionIdAndIdNot(
                "MOCK", "txn-confirmed-order", PAYMENT_ID)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.handleCallback(
                "MOCK", callback, MockPaymentProvider.signCallback(CALLBACK_SECRET, callback));

        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("CONFIRMED", order.getStatus());
    }

    @Test
    void repeatedPaidCallbackIsIdempotent() {
        Payment payment = payment("PAID");
        payment.setProviderTransactionId("txn-success-1");
        order.setPaymentStatus("PAID");
        PaymentCallbackRequest callback = callback("125000.00", "txn-success-1", "PAID");
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        PaymentResponse result = paymentService.handleCallback(
                "MOCK", callback, MockPaymentProvider.signCallback(CALLBACK_SECRET, callback));

        assertEquals("PAID", result.status());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void callbackAmountMismatchIsRejectedBeforeChangingState() {
        Payment payment = payment("PENDING");
        PaymentCallbackRequest callback = callback("1.00", "txn-wrong-amount", "PAID");
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.handleCallback(
                        "MOCK", callback, MockPaymentProvider.signCallback(CALLBACK_SECRET, callback)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("PENDING", payment.getStatus());
        assertEquals("UNPAID", order.getPaymentStatus());
    }

    @Test
    void duplicateProviderTransactionForAnotherPaymentIsRejected() {
        Payment payment = payment("PENDING");
        PaymentCallbackRequest callback = callback("125000.00", "txn-already-used", "PAID");
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByProviderAndProviderTransactionIdAndIdNot(
                "MOCK", "txn-already-used", PAYMENT_ID)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.handleCallback(
                        "MOCK", callback, MockPaymentProvider.signCallback(CALLBACK_SECRET, callback)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("PENDING", payment.getStatus());
    }

    @Test
    void failedCallbackLeavesOrderUnpaidAndAllowsPaymentAttemptFailureState() {
        Payment payment = payment("PENDING");
        PaymentCallbackRequest callback = callback("125000.00", "txn-failed-1", "FAILED");
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.existsByProviderAndProviderTransactionIdAndIdNot(
                "MOCK", "txn-failed-1", PAYMENT_ID)).thenReturn(false);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse result = paymentService.handleCallback(
                "MOCK", callback, MockPaymentProvider.signCallback(CALLBACK_SECRET, callback));

        assertEquals("FAILED", result.status());
        assertEquals("UNPAID", order.getPaymentStatus());
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    void invalidCallbackSignatureIsRejected() {
        PaymentCallbackRequest callback = callback("125000.00", "txn-invalid-signature", "PAID");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.handleCallback("MOCK", callback, "bad-signature"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(paymentRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(paymentRepository, never()).findById(any(UUID.class));
    }

    @Test
    void paidOrderCannotBeCancelledByCustomerOrAdmin() {
        order.setPaymentStatus("PAID");
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        OrderService orderService = new OrderService(
                orderRepository,
                mock(CartRepository.class),
                mock(AddressRepository.class),
                mock(UserRepository.class),
                mock(ProductRepository.class),
                mock(com.smart_ecommerce.backend.repository.ProductImageRepository.class),
                mock(ProductVariantRepository.class),
                mock(CouponRepository.class),
                mock(CouponService.class),
                paymentService,
                mock(ShippingRepository.class),
                mock(ShopOrderRepository.class)
        );

        ResponseStatusException customerException = assertThrows(
                ResponseStatusException.class, () -> orderService.cancel(USER_ID, ORDER_ID));
        ResponseStatusException adminException = assertThrows(
                ResponseStatusException.class, () -> orderService.updateStatus(ORDER_ID, "CANCELLED"));

        assertEquals(HttpStatus.CONFLICT, customerException.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, adminException.getStatusCode());
    }

    private Payment payment(String status) {
        return Payment.builder()
                .id(PAYMENT_ID)
                .order(order)
                .user(user)
                .paymentMethod("VNPAY")
                .provider("MOCK")
                .amount(new BigDecimal("125000.00"))
                .currency("VND")
                .status(status)
                .idempotencyKey("payment-test-key")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PaymentCallbackRequest callback(String amount, String transactionId, String status) {
        return new PaymentCallbackRequest(
                PAYMENT_ID,
                transactionId,
                new BigDecimal(amount),
                "VND",
                status);
    }
}
