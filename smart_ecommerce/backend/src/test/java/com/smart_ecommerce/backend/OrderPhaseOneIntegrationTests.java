package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.cart.AddCartItemRequest;
import com.smart_ecommerce.backend.dto.order.CreateOrderRequest;
import com.smart_ecommerce.backend.dto.order.OrderResponse;
import com.smart_ecommerce.backend.entity.Address;
import com.smart_ecommerce.backend.entity.Cart;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductVariant;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.AddressRepository;
import com.smart_ecommerce.backend.repository.CartRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductVariantRepository;
import com.smart_ecommerce.backend.repository.PaymentRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import com.smart_ecommerce.backend.service.OrderService;
import com.smart_ecommerce.backend.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@Rollback
class OrderPhaseOneIntegrationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void createAndCancelOrderDeductsThenRestoresProductInventory() {
        Fixture fixture = createFixture(10, 2);

        OrderResponse created = orderService.create(
                fixture.user().getId(),
                new CreateOrderRequest(fixture.address().getId(), "COD", "phase-one-test", null));

        assertEquals("PENDING", created.getStatus());
        assertEquals(new BigDecimal("250.00"), created.getSubtotal());
        assertEquals(new BigDecimal("250.00"), created.getTotalAmount());
        assertEquals("Snapshot Product", created.getItems().get(0).getProductName());
        assertEquals(8, productRepository.findById(fixture.product().getId()).orElseThrow().getQuantity());

        OrderResponse cancelled = orderService.cancel(fixture.user().getId(), created.getId());

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("CANCELLED", paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(created.getId()).get(0).getStatus());
        assertEquals(10, productRepository.findById(fixture.product().getId()).orElseThrow().getQuantity());
    }

    @Test
    void adminStatusTransitionsAcceptOnlyTheConfiguredOrderFlow() {
        Fixture fixture = createFixture(6, 1);
        OrderResponse created = orderService.create(
                fixture.user().getId(),
                new CreateOrderRequest(fixture.address().getId(), "COD", null, null));

        assertEquals("CONFIRMED", orderService.updateStatus(created.getId(), "confirmed").getStatus());
        assertEquals("SHIPPING", orderService.updateStatus(created.getId(), "SHIPPING").getStatus());
        OrderResponse delivered = orderService.updateStatus(created.getId(), "DELIVERED");
        assertEquals("DELIVERED", delivered.getStatus());
        assertEquals("PAID", delivered.getPaymentStatus());
        assertEquals("PAID", paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(created.getId()).get(0).getStatus());
        assertEquals(5, productRepository.findById(fixture.product().getId()).orElseThrow().getQuantity());
    }

    @Test
    void onlineOrderCannotShipBeforePaymentAndDeliveredOrderCannotBeCancelled() {
        Fixture onlineFixture = createFixture(5, 1);
        OrderResponse onlineOrder = orderService.create(
                onlineFixture.user().getId(),
                new CreateOrderRequest(onlineFixture.address().getId(), "VNPAY", null, null));
        orderService.updateStatus(onlineOrder.getId(), "CONFIRMED");

        ResponseStatusException unpaidError = assertThrows(
                ResponseStatusException.class,
                () -> orderService.updateStatus(onlineOrder.getId(), "SHIPPING"));
        assertEquals(HttpStatus.CONFLICT, unpaidError.getStatusCode());

        Fixture codFixture = createFixture(5, 1);
        OrderResponse codOrder = orderService.create(
                codFixture.user().getId(),
                new CreateOrderRequest(codFixture.address().getId(), "COD", null, null));
        orderService.updateStatus(codOrder.getId(), "CONFIRMED");
        orderService.updateStatus(codOrder.getId(), "SHIPPING");
        orderService.updateStatus(codOrder.getId(), "DELIVERED");

        ResponseStatusException cancellationError = assertThrows(
                ResponseStatusException.class,
                () -> orderService.updateStatus(codOrder.getId(), "CANCELLED"));
        assertEquals(HttpStatus.CONFLICT, cancellationError.getStatusCode());
    }

    @Test
    void invalidTransitionAndForeignCustomerCancellationAreRejected() {
        Fixture fixture = createFixture(5, 1);
        OrderResponse created = orderService.create(
                fixture.user().getId(),
                new CreateOrderRequest(fixture.address().getId(), "COD", null, null));

        ResponseStatusException transitionError = assertThrows(
                ResponseStatusException.class,
                () -> orderService.updateStatus(created.getId(), "DELIVERED"));
        assertEquals(HttpStatus.CONFLICT, transitionError.getStatusCode());

        ResponseStatusException ownershipError = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancel(UUID.randomUUID(), created.getId()));
        assertEquals(HttpStatus.NOT_FOUND, ownershipError.getStatusCode());
    }

    @Test
    void orderRequiresVariantWhenProductHasVariants() {
        Fixture fixture = createFixture(10, 1);
        saveVariant(fixture, 4);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.create(
                        fixture.user().getId(),
                        new CreateOrderRequest(fixture.address().getId(), "COD", null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void variantOrderUsesAndRestoresVariantInventory() {
        Fixture fixture = createFixture(10, 2);
        ProductVariant variant = saveVariant(fixture, 4);
        Cart cart = cartRepository.findByUserId(fixture.user().getId()).orElseThrow();
        int requestedQuantity = cart.getItems().get(0).getQuantity();
        cartService.removeItem(fixture.user().getId(), cart.getItems().get(0).getId());
        cartService.addItem(
                fixture.user().getId(),
                new AddCartItemRequest(fixture.product().getId(), variant.getId(), requestedQuantity));

        OrderResponse created = orderService.create(
                fixture.user().getId(),
                new CreateOrderRequest(fixture.address().getId(), "COD", null, null));

        assertEquals(new BigDecimal("220.00"), created.getSubtotal());
        assertEquals(10, productRepository.findById(fixture.product().getId()).orElseThrow().getQuantity());
        assertEquals(2, variantRepository.findById(variant.getId()).orElseThrow().getQuantity());

        orderService.cancel(fixture.user().getId(), created.getId());

        assertEquals(4, variantRepository.findById(variant.getId()).orElseThrow().getQuantity());
    }

    private Fixture createFixture(int stock, int cartQuantity) {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .fullName("Phase One Test")
                .email("phase-one-" + suffix + "@example.test")
                .password("test-hash")
                .status("ACTIVE")
                .build());

        Product product = productRepository.save(Product.builder()
                .name("Snapshot Product")
                .slug("phase-one-" + suffix.toLowerCase(Locale.ROOT))
                .sku("PHASE-" + suffix.substring(0, 12).toUpperCase(Locale.ROOT))
                .price(new BigDecimal("125.00"))
                .quantity(stock)
                .status("ACTIVE")
                .build());

        Address address = addressRepository.save(Address.builder()
                .user(user)
                .recipientName("Phase One Test")
                .phone("0900000000")
                .province("Ho Chi Minh")
                .district("District 1")
                .ward("Ward 1")
                .addressDetail("Test address")
                .isDefault(false)
                .build());

        cartService.getOrCreateCart(user.getId());
        cartService.addItem(user.getId(), new AddCartItemRequest(product.getId(), null, cartQuantity));

        return new Fixture(user, product, address);
    }

    private ProductVariant saveVariant(Fixture fixture, int stock) {
        return variantRepository.save(ProductVariant.builder()
                .product(fixture.product())
                .sku("PHASE-V-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT))
                .price(new BigDecimal("110.00"))
                .quantity(stock)
                .status("ACTIVE")
                .build());
    }

    private record Fixture(User user, Product product, Address address) {
    }
}
