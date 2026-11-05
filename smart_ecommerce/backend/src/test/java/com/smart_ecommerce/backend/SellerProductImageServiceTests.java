package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.productimage.ProductImageRequest;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductImage;
import com.smart_ecommerce.backend.entity.Shop;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ShopRepository;
import com.smart_ecommerce.backend.service.SellerProductImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerProductImageServiceTests {
    private final UUID ownerA = UUID.randomUUID();
    private final UUID shopAId = UUID.randomUUID();
    private final UUID shopBId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();
    private ShopRepository shops;
    private ProductRepository products;
    private ProductImageRepository images;
    private SellerProductImageService service;

    @BeforeEach
    void setUp() {
        shops = mock(ShopRepository.class);
        products = mock(ProductRepository.class);
        images = mock(ProductImageRepository.class);
        service = new SellerProductImageService(shops, products, images, mock(com.smart_ecommerce.backend.service.ProductMediaStorage.class));
        when(shops.findByOwnerId(ownerA)).thenReturn(Optional.of(
            Shop.builder().id(shopAId).status("ACTIVE").build()));
    }

    @Test
    void sellerCannotAddImageToAnotherShopsProduct() {
        Product foreign = Product.builder().id(productId)
            .shop(Shop.builder().id(shopBId).build()).build();
        when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(foreign));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.add(ownerA, productId,
                new ProductImageRequest("https://example.org/photo.jpg", true, 0)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(images, never()).saveAndFlush(any());
    }

    @Test
    void sellerCannotDeleteImageFromAnotherProduct() {
        Product own = Product.builder().id(productId)
            .shop(Shop.builder().id(shopAId).build()).build();
        Product foreign = Product.builder().id(UUID.randomUUID())
            .shop(Shop.builder().id(shopBId).build()).build();
        when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(own));
        when(images.findById(imageId)).thenReturn(Optional.of(
            ProductImage.builder().id(imageId).product(foreign).build()));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.remove(ownerA, productId, imageId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(images, never()).delete(any());
    }

    @Test
    void inactiveShopCannotManageImages() {
        when(shops.findByOwnerId(ownerA)).thenReturn(Optional.of(
            Shop.builder().id(shopAId).status("SUSPENDED").build()));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.list(ownerA, productId));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(products, images);
    }
}
