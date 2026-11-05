package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.cart.AddCartItemRequest;
import com.smart_ecommerce.backend.dto.order.CreateOrderRequest;
import com.smart_ecommerce.backend.dto.order.OrderResponse;
import com.smart_ecommerce.backend.dto.shipping.CreateShippingRequest;
import com.smart_ecommerce.backend.dto.shipping.ShippingResponse;
import com.smart_ecommerce.backend.entity.Address;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.AddressRepository;
import com.smart_ecommerce.backend.repository.PaymentRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import com.smart_ecommerce.backend.service.CartService;
import com.smart_ecommerce.backend.service.OrderService;
import com.smart_ecommerce.backend.service.ShippingService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@Rollback
class ShippingIntegrationTests {

    @Autowired
    private ShippingService shippingService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void codShippingLifecycleCompletesCodPaymentOnlyAfterDelivery() {
        Fixture fixture = createFixture();
        OrderResponse order = orderService.create(
                fixture.user().getId(),
                new CreateOrderRequest(fixture.address().getId(), "COD", null, null));

        assertEquals("PENDING", paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(order.getId()).get(0).getStatus());
        orderService.updateStatus(order.getId(), "CONFIRMED");
        ShippingResponse created = shippingService.create(
                order.getId(), new CreateShippingRequest(null));

        assertTrue(created.trackingCode().startsWith("MOCK-"));
        assertEquals(order.getShippingFee(), created.shippingFee());
        assertEquals("READY", created.shippingStatus());

        ShippingResponse inTransit = shippingService.updateStatus(created.id(), "IN_TRANSIT");
        assertEquals("IN_TRANSIT", inTransit.shippingStatus());
        assertEquals("SHIPPING", orderService.getOrderForManagement(order.getId()).getStatus());
        assertEquals("UNPAID", orderService.getOrderForManagement(order.getId()).getPaymentStatus());

        ShippingResponse delivered = shippingService.updateStatus(created.id(), "DELIVERED");
        OrderResponse completedOrder = orderService.getOrderForManagement(order.getId());
        assertEquals("DELIVERED", delivered.shippingStatus());
        assertEquals("DELIVERED", completedOrder.getStatus());
        assertEquals("PAID", completedOrder.getPaymentStatus());
        assertEquals("PAID", paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(order.getId()).get(0).getStatus());
    }

    @Test
    void shippingRejectsInvalidTransitionAndDuplicateShipment() {
        Fixture fixture = createFixture();
        OrderResponse order = orderService.create(
                fixture.user().getId(),
                new CreateOrderRequest(fixture.address().getId(), "COD", null, null));
        orderService.updateStatus(order.getId(), "CONFIRMED");
        ShippingResponse created = shippingService.create(
                order.getId(), new CreateShippingRequest("TRACK-" + UUID.randomUUID()));

        ResponseStatusException transitionError = assertThrows(
                ResponseStatusException.class,
                () -> shippingService.updateStatus(created.id(), "DELIVERED"));
        ResponseStatusException duplicateError = assertThrows(
                ResponseStatusException.class,
                () -> shippingService.create(order.getId(), new CreateShippingRequest(null)));

        assertEquals(HttpStatus.CONFLICT, transitionError.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, duplicateError.getStatusCode());
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .fullName("Shipping Test")
                .email("shipping-" + suffix + "@example.test")
                .password("test-hash")
                .status("ACTIVE")
                .build());

        Product product = productRepository.save(Product.builder()
                .name("Shipping Test Product")
                .slug("shipping-" + suffix.toLowerCase(Locale.ROOT))
                .sku("SHIP-" + suffix.substring(0, 12).toUpperCase(Locale.ROOT))
                .price(new BigDecimal("125.00"))
                .quantity(5)
                .status("ACTIVE")
                .build());

        Address address = addressRepository.save(Address.builder()
                .user(user)
                .recipientName("Shipping Test")
                .phone("0900000000")
                .province("Ho Chi Minh")
                .district("District 1")
                .ward("Ward 1")
                .addressDetail("Test address")
                .isDefault(false)
                .build());

        cartService.getOrCreateCart(user.getId());
        cartService.addItem(user.getId(), new AddCartItemRequest(product.getId(), null, 1));
        return new Fixture(user, address);
    }

    private record Fixture(User user, Address address) {
    }
}
