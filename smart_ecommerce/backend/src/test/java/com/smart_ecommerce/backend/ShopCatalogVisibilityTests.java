package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.cart.AddCartItemRequest;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductVariant;
import com.smart_ecommerce.backend.entity.Shop;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.CartService;
import com.smart_ecommerce.backend.service.ProductImageService;
import com.smart_ecommerce.backend.service.ProductService;
import com.smart_ecommerce.backend.service.ProductVariantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Regression checks for customer-facing shop suspension handling. */
class ShopCatalogVisibilityTests {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final CartItemRepository cartItems = mock(CartItemRepository.class);
    private final CartRepository carts = mock(CartRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);

    private Product product(String shopStatus) {
        return Product.builder()
                .id(UUID.randomUUID())
                .status("ACTIVE")
                .quantity(10)
                .shop(Shop.builder().id(UUID.randomUUID()).status(shopStatus).build())
                .build();
    }

    @Test
    void publicProductLookupUsesShopFilteredRepository() {
        UUID productId = UUID.randomUUID();
        ProductService service = new ProductService(products, mock(CategoryRepository.class), images);
        when(products.findVisibleCatalogById(productId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getActiveById(productId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(products).findVisibleCatalogById(productId);
        verify(products, never()).findById(productId);
    }

    @Test
    void suspendedShopProductCannotBeAddedToCart() {
        Product product = product("SUSPENDED");
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        CartService service = new CartService(carts, cartItems, products, images, variants, users);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addItem(UUID.randomUUID(), new AddCartItemRequest(product.getId(), null, 1)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(carts, cartItems, variants, users);
    }

    @Test
    void pendingShopProductVariantsAreNotPublic() {
        Product product = product("PENDING");
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        ProductVariantService service = new ProductVariantService(variants, products, org.mockito.Mockito.mock(com.smart_ecommerce.backend.repository.ProductImageRepository.class), cartItems, orderItems);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getByProduct(product.getId()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(variants, never()).findByProductIdAndStatusIgnoreCase(any(), any());
    }

    @Test
    void suspendedShopVariantCannotBeFetchedDirectly() {
        Product product = product("SUSPENDED");
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId).product(product).status("ACTIVE").build();
        when(variants.findByIdAndStatusIgnoreCase(variantId, "ACTIVE"))
                .thenReturn(Optional.of(variant));
        ProductVariantService service = new ProductVariantService(variants, products, org.mockito.Mockito.mock(com.smart_ecommerce.backend.repository.ProductImageRepository.class), cartItems, orderItems);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getActiveById(variantId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void suspendedShopProductImagesAreNotPublic() {
        Product product = product("SUSPENDED");
        when(products.findVisibleCatalogById(product.getId())).thenReturn(Optional.empty());
        ProductImageService service = new ProductImageService(images, products);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getByProduct(product.getId()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(images);
    }
}
