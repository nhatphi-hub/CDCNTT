package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.seller.SellerAnalytics;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.SellerOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerAnalyticsTests {
    private final ShopRepository shops = mock(ShopRepository.class);
    private final ShopOrderRepository shopOrders = mock(ShopOrderRepository.class);
    private final SellerOrderItemRepository items = mock(SellerOrderItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final SellerOrderService service = new SellerOrderService(shops, shopOrders, items, orders);
    private final UUID me = UUID.randomUUID();
    private final UUID myShop = UUID.randomUUID();
    private final UUID otherShop = UUID.randomUUID();
    private Shop shop(UUID id) { return Shop.builder().id(id).status("ACTIVE").build(); }
    private void permit() { when(shops.findByOwnerId(me)).thenReturn(Optional.of(shop(myShop))); }

    @Test void invalidRangeIsRejected() {
        permit();
        var e = assertThrows(ResponseStatusException.class, () -> service.analytics(me, 365));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(shopOrders, never()).findByShop_Id(any());
    }

    @Test void onlyPaidDeliveredOwnItemsCountTowardRevenueAndTopProducts() {
        permit();
        UUID productA = UUID.randomUUID(), productB = UUID.randomUUID();
        Order o = Order.builder().id(UUID.randomUUID()).createdAt(LocalDateTime.now())
            .status("DELIVERED").paymentStatus("PAID")
            .subtotal(new BigDecimal("549.00")).discountAmount(new BigDecimal("49.00"))
            .shippingFee(new BigDecimal("15.00")).build();
        OrderItem a = OrderItem.builder().id(UUID.randomUUID()).order(o).shop(shop(myShop))
            .product(Product.builder().id(productA).build()).productNameSnapshot("Áo")
            .quantity(2).subtotal(new BigDecimal("199.00")).build();
        OrderItem b = OrderItem.builder().id(UUID.randomUUID()).order(o).shop(shop(otherShop))
            .product(Product.builder().id(productB).build()).productNameSnapshot("Tai nghe")
            .quantity(1).subtotal(new BigDecimal("350.00")).build();
        o.setItems(new ArrayList<>(List.of(a, b)));
        ShopOrder segment = ShopOrder.builder().order(o).shop(shop(myShop)).status("DELIVERED").build();
        when(shopOrders.findByShop_Id(myShop)).thenReturn(List.of(segment));
        when(items.findByOrder_IdAndShop_Id(o.getId(), myShop)).thenReturn(List.of(a));
        SellerAnalytics result = service.analytics(me, 30);
        assertEquals(1, result.totalOrders());
        assertEquals(1, result.completedOrders());
        assertEquals(2, result.unitsSold());
        assertEquals(1, result.topProducts().size());
        assertEquals(productA, result.topProducts().get(0).productId());
        assertEquals(1, result.dailyRevenue().stream().filter(d -> d.completedOrders() == 1).count());
        assertTrue(result.paidDeliveredRevenue().compareTo(new BigDecimal("199.00")) < 0);
        assertTrue(result.paidDeliveredRevenue().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test void pendingUnpaidOrdersDoNotCountAsRevenue() {
        permit();
        Order o = Order.builder().id(UUID.randomUUID()).createdAt(LocalDateTime.now())
            .status("PENDING").paymentStatus("UNPAID").build();
        OrderItem own = OrderItem.builder().id(UUID.randomUUID())
            .product(Product.builder().id(UUID.randomUUID()).build())
            .quantity(1).subtotal(new BigDecimal("100.00")).build();
        when(shopOrders.findByShop_Id(myShop)).thenReturn(List.of(
            ShopOrder.builder().order(o).shop(shop(myShop)).status("READY").build()));
        when(items.findByOrder_IdAndShop_Id(o.getId(), myShop)).thenReturn(List.of(own));
        SellerAnalytics result = service.analytics(me, 7);
        assertEquals(1, result.totalOrders());
        assertEquals(0, result.completedOrders());
        assertEquals(0, result.unitsSold());
        assertEquals(0, result.paidDeliveredRevenue().signum());
    }

    @Test void unapprovedShopCannotReadAnalytics() {
        when(shops.findByOwnerId(me)).thenReturn(Optional.of(Shop.builder().id(myShop).status("PENDING").build()));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.analytics(me, 7)).getStatusCode());
    }
}
