package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.SellerOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerOrderServiceTests {
    private final ShopRepository shops=mock(ShopRepository.class);
    private final ShopOrderRepository segments=mock(ShopOrderRepository.class);
    private final SellerOrderItemRepository items=mock(SellerOrderItemRepository.class);
    private final OrderRepository orders=mock(OrderRepository.class);
    private final SellerOrderService service=new SellerOrderService(shops, segments, items, orders);
    private final UUID userA=UUID.randomUUID(), shopA=UUID.randomUUID(), shopB=UUID.randomUUID();
    private final UUID orderId=UUID.randomUUID();

    private Shop shop(UUID id) { return Shop.builder().id(id).status("ACTIVE").build(); }
    private void permit() { when(shops.findByOwnerId(userA)).thenReturn(Optional.of(shop(shopA))); }
    private OrderItem item(UUID id, UUID s, BigDecimal gross) {
        Product product=Product.builder().id(UUID.randomUUID()).build();
        return OrderItem.builder().id(id).product(product).shop(shop(s)).productNameSnapshot("Item")
            .quantity(1).unitPrice(gross).subtotal(gross).build();
    }
    private Order order() {
        return Order.builder().id(orderId).orderCode("ORD-TEST").status("PENDING").paymentStatus("UNPAID")
            .paymentMethod("COD").subtotal(new BigDecimal("549.00"))
            .discountAmount(new BigDecimal("49.00")).shippingRecipientName("Buyer")
            .shippingPhone("000").shippingAddressDetail("Street").shippingWard("Ward")
            .shippingDistrict("District").shippingProvince("City").build();
    }
    @Test void foreignShopOrderCannotBeRead() {
        permit();
        ResponseStatusException e=assertThrows(ResponseStatusException.class, () -> service.get(userA,orderId));
        assertEquals(HttpStatus.NOT_FOUND,e.getStatusCode());
    }
    @Test void foreignShopOrderCannotBeChangedByUrl() {
        permit(); Order o=order();
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(o));
        ResponseStatusException e=assertThrows(ResponseStatusException.class,
            () -> service.transition(userA,orderId,"CONFIRMED"));
        assertEquals(HttpStatus.NOT_FOUND,e.getStatusCode());
        verify(segments,never()).saveAndFlush(any());
    }
    @Test void confirmsOnlyAWhileBRemainsPendingAndGlobalPending() {
        permit(); Order o=order();
        ShopOrder a=ShopOrder.builder().id(UUID.randomUUID()).order(o).shop(shop(shopA)).status("PENDING").build();
        ShopOrder b=ShopOrder.builder().id(UUID.randomUUID()).order(o).shop(shop(shopB)).status("PENDING").build();
        OrderItem ia=item(UUID.randomUUID(),shopA,new BigDecimal("199.00"));
        OrderItem ib=item(UUID.randomUUID(),shopB,new BigDecimal("350.00"));
        o.setItems(new ArrayList<>(List.of(ia,ib)));
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(o));
        when(segments.lockOwned(orderId,shopA)).thenReturn(Optional.of(a));
        when(segments.findByOrder_Id(orderId)).thenReturn(List.of(a,b));
        when(items.findByOrder_IdAndShop_Id(orderId,shopA)).thenReturn(List.of(ia));
        service.transition(userA,orderId,"CONFIRMED");
        assertEquals("CONFIRMED",a.getStatus());
        assertEquals("PENDING",b.getStatus());
        assertEquals("PENDING",o.getStatus());
        verify(segments).saveAndFlush(a);
    }
    @Test void unapprovedShopCannotManageOrders() {
        when(shops.findByOwnerId(userA)).thenReturn(Optional.of(Shop.builder().id(shopA).status("PENDING").build()));
        ResponseStatusException e=assertThrows(ResponseStatusException.class, () -> service.get(userA,orderId));
        assertEquals(HttpStatus.FORBIDDEN,e.getStatusCode());
    }
    @Test void cannotSkipConfirmedAndMoveDirectlyToReady() {
        permit(); Order o=order();
        ShopOrder a=ShopOrder.builder().order(o).shop(shop(shopA)).status("PENDING").build();
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(o));
        when(segments.lockOwned(orderId,shopA)).thenReturn(Optional.of(a));
        ResponseStatusException e=assertThrows(ResponseStatusException.class,
            () -> service.transition(userA,orderId,"READY"));
        assertEquals(HttpStatus.CONFLICT,e.getStatusCode());
    }
    @Test void shopRevenueUsesOnlyOwnLinesDiscountAndPaidDelivered() {
        permit(); Order o=order();o.setStatus("DELIVERED");o.setPaymentStatus("PAID");
        OrderItem ia=item(UUID.randomUUID(),shopA,new BigDecimal("199.00"));
        OrderItem ib=item(UUID.randomUUID(),shopB,new BigDecimal("350.00"));
        o.setItems(new ArrayList<>(List.of(ia,ib)));
        ShopOrder a=ShopOrder.builder().order(o).shop(shop(shopA)).status("DELIVERED").build();
        when(segments.findByShop_Id(shopA)).thenReturn(List.of(a));
        when(items.findByOrder_IdAndShop_Id(orderId,shopA)).thenReturn(List.of(ia));
        BigDecimal revenue=service.stats(userA).paidDeliveredRevenue();
        assertTrue(revenue.compareTo(new BigDecimal("199.00")) < 0);
        assertTrue(revenue.compareTo(BigDecimal.ZERO) > 0);
        assertEquals(1,service.stats(userA).totalOrders());
    }
}
