package com.smart_ecommerce.backend;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerCouponAuthorizationTests {
    @Test void cannotAlterOtherShopsCoupon() {
        UUID owner=UUID.randomUUID(), shopA=UUID.randomUUID(), couponId=UUID.randomUUID();
        ShopRepository shops=mock(ShopRepository.class);
        CouponRepository coupons=mock(CouponRepository.class);
        when(shops.findByOwnerId(owner)).thenReturn(Optional.of(Shop.builder().id(shopA).status("ACTIVE").build()));
        when(coupons.findById(couponId)).thenReturn(Optional.of(Coupon.builder()
          .id(couponId).shop(Shop.builder().id(UUID.randomUUID()).build()).build()));
        SellerCouponService service=new SellerCouponService(coupons,shops);
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->service.setActive(owner,couponId,false));
        assertEquals(HttpStatus.NOT_FOUND,ex.getStatusCode());
        verify(coupons,never()).saveAndFlush(any(Coupon.class));
    }
    @Test void shopDiscountUsesOnlyIssuerShopSubtotal() {
        UUID shopId=UUID.randomUUID();
        CouponRepository repo=mock(CouponRepository.class);
        Coupon coupon=Coupon.builder().id(UUID.randomUUID()).code("SHOP10")
          .shop(Shop.builder().id(shopId).status("ACTIVE").build())
          .active(true).startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(1))
          .usedCount(0).discountType(Coupon.DiscountType.PERCENT).discountValue(new BigDecimal("10")).build();
        when(repo.findByCodeIgnoreCaseForUpdate("SHOP10")).thenReturn(Optional.of(coupon));
        when(repo.incrementUsedCount(coupon.getId())).thenReturn(1);
        var result=new CouponService(repo).applyToOrder("SHOP10",new BigDecimal("1000"),
            Map.of(shopId,new BigDecimal("200")));
        assertEquals(0,new BigDecimal("20.00").compareTo(result.discountAmount()));
    }
}
