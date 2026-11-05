package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.Shop;
import com.smart_ecommerce.backend.repository.ReviewRepository;
import com.smart_ecommerce.backend.repository.ShopRepository;
import com.smart_ecommerce.backend.service.SellerReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerReviewsAuthorizationTests {
    private final UUID sellerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID shopId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private ShopRepository shops;
    private ReviewRepository reviews;
    private SellerReviewService service;

    @BeforeEach
    void setup() {
        shops = mock(ShopRepository.class);
        reviews = mock(ReviewRepository.class);
        service = new SellerReviewService(shops, reviews, mock(com.smart_ecommerce.backend.repository.ReviewReplyRepository.class));
    }

    @Test
    void sellerWithoutShopIsDenied() {
        when(shops.findByOwnerId(sellerId)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.mine(sellerId));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(reviews);
    }

    @Test
    void suspendedShopIsDenied() {
        when(shops.findByOwnerId(sellerId)).thenReturn(Optional.of(
                Shop.builder().id(shopId).status("SUSPENDED").build()));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.mine(sellerId));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(reviews);
    }

    @Test
    void activeSellerQueriesOnlyTheirShop() {
        when(shops.findByOwnerId(sellerId)).thenReturn(Optional.of(
                Shop.builder().id(shopId).status("ACTIVE").build()));
        when(reviews.findReviewsOfShop(shopId)).thenReturn(List.of());
        assertTrue(service.mine(sellerId).isEmpty());
        verify(reviews).findReviewsOfShop(shopId);
        verifyNoMoreInteractions(reviews);
    }
}
