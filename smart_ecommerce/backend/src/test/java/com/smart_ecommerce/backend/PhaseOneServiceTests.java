package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.cart.AddCartItemRequest;
import com.smart_ecommerce.backend.dto.product.ProductRequest;
import com.smart_ecommerce.backend.dto.productvariant.ProductVariantRequest;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductVariant;
import com.smart_ecommerce.backend.repository.CartItemRepository;
import com.smart_ecommerce.backend.repository.CartRepository;
import com.smart_ecommerce.backend.repository.CategoryRepository;
import com.smart_ecommerce.backend.repository.OrderItemRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductVariantRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import com.smart_ecommerce.backend.service.CartService;
import com.smart_ecommerce.backend.service.ProductService;
import com.smart_ecommerce.backend.service.ProductVariantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.smart_ecommerce.backend.repository.ProductImageRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhaseOneServiceTests {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final CartItemRepository cartItemRepository = mock(CartItemRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final CartRepository cartRepository = mock(CartRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private ProductService productService;
    private ProductVariantService variantService;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository,
                categoryRepository,
                mock(ProductImageRepository.class)
                );
        variantService = new ProductVariantService(
                variantRepository, productRepository, mock(ProductImageRepository.class), cartItemRepository, orderItemRepository);
        cartService = new CartService(
                cartRepository, cartItemRepository, productRepository,
                mock(ProductImageRepository.class), variantRepository, userRepository);
    }

    @Test
    void productCreationRejectsBlankSkuAsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.create(productRequest("  ", null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository, categoryRepository);
    }

    @Test
    void productCreationDefaultsStatusToActiveAndTrimsSku() {
        when(productRepository.existsBySlug("sample-product")).thenReturn(false);
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = productService.create(productRequest(" SKU-1 ", null));

        assertEquals("SKU-1", response.sku());
        assertEquals("ACTIVE", response.status());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void productCreationRejectsUnsupportedStatusAsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.create(productRequest("SKU-1", "ARCHIVED")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository, categoryRepository);
    }

    @Test
    void publicProductListQueriesOnlyActiveProducts() {
        when(productRepository.findVisibleCatalog()).thenReturn(List.of());

        productService.getAll();

        verify(productRepository).findVisibleCatalog();
        verify(productRepository, never()).findAll();
    }

    @Test
    void variantCreationRejectsUnsupportedStatusAsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> variantService.create(variantRequest(UUID.randomUUID(), "SKU-V1", "REMOVED")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository, variantRepository);
    }

    @Test
    void variantCreationRejectsMissingProductAsNotFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> variantService.create(variantRequest(productId, "SKU-V1", "ACTIVE")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void cartRejectsInactiveProductBeforeCreatingCart() {
        UUID productId = UUID.randomUUID();
        Product inactiveProduct = Product.builder()
                .id(productId)
                .status("INACTIVE")
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(inactiveProduct));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> cartService.addItem(UUID.randomUUID(), new AddCartItemRequest(productId, null, 1)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(cartRepository, cartItemRepository, userRepository, variantRepository);
    }

    @Test
    void cartRequiresVariantWhenProductHasVariants() {
        UUID productId = UUID.randomUUID();
        Product activeProduct = Product.builder()
                .id(productId)
                .status("ACTIVE")
                .quantity(3)
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(activeProduct));
        when(variantRepository.existsByProductId(productId)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> cartService.addItem(UUID.randomUUID(), new AddCartItemRequest(productId, null, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(cartRepository, cartItemRepository, userRepository);
    }

    @Test
    void cartRejectsVariantThatBelongsToAnotherProduct() {
        UUID productId = UUID.randomUUID();
        UUID otherProductId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product activeProduct = Product.builder().id(productId).status("ACTIVE").build();
        Product otherProduct = Product.builder().id(otherProductId).status("ACTIVE").build();
        ProductVariant foreignVariant = ProductVariant.builder()
                .id(variantId)
                .product(otherProduct)
                .status("ACTIVE")
                .quantity(3)
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(activeProduct));
        when(variantRepository.existsByProductId(productId)).thenReturn(true);
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(foreignVariant));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> cartService.addItem(UUID.randomUUID(), new AddCartItemRequest(productId, variantId, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(cartRepository, cartItemRepository, userRepository);
    }

    private ProductRequest productRequest(String sku, String status) {
        return new ProductRequest(
                "Sample Product", "sample-product", sku, null, null,
                new BigDecimal("100.00"), null, 5,
                null, null, status, null);
    }

    private ProductVariantRequest variantRequest(UUID productId, String sku, String status) {
        return new ProductVariantRequest(
                productId, "M", "Blue", sku, new BigDecimal("100.00"),
                null, 2, status);
    }

    @Test
    void variantUsedByOrderCannotBeDeleted() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder().id(variantId).build();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(cartItemRepository.existsByVariantId(variantId)).thenReturn(false);
        when(orderItemRepository.existsByVariantReference(variantId)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> variantService.delete(variantId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(variantRepository, never()).delete(variant);
    }
}
