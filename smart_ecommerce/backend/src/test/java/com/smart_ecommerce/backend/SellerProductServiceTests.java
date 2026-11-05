package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.product.ProductRequest;
import com.smart_ecommerce.backend.dto.productvariant.ProductVariantRequest;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.SellerProductService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellerProductServiceTests {
    private final ShopRepository shops = mock(ShopRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final CartItemRepository cartItems = mock(CartItemRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final SellerProductService service = new SellerProductService(shops, products, variants, categories, images, cartItems, orderItems);
    private final UUID ownerA = UUID.randomUUID(), ownerB = UUID.randomUUID();
    private final UUID shopA = UUID.randomUUID(), shopB = UUID.randomUUID();
    private final UUID productA = UUID.randomUUID(), productB = UUID.randomUUID();
    private final UUID variantB = UUID.randomUUID();

    private void activeShops() {
        when(shops.findByOwnerId(ownerA)).thenReturn(Optional.of(Shop.builder().id(shopA).status("ACTIVE").build()));
        when(shops.findByOwnerId(ownerB)).thenReturn(Optional.of(Shop.builder().id(shopB).status("ACTIVE").build()));
    }
    private void notFound(Runnable action) {
        ResponseStatusException ex=assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.NOT_FOUND,ex.getStatusCode());
    }
    private ProductRequest request() {
        return new ProductRequest("Product A", "product-a", "A-01", null, null,
            new BigDecimal("100"), null, 3, "UNISEX", null, "ACTIVE", null);
    }
    private ProductVariantRequest variantRequest(UUID parent) {
        return new ProductVariantRequest(parent,"M","Blue","B-01",null,null,3,"ACTIVE");
    }
    @Test void createIgnoresAnyClientChosenShopAndUsesAuthenticatedOwner() {
        activeShops();
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p=inv.getArgument(0);
            assertEquals(shopA,p.getShop().getId());
            p.setId(productA);return p;
        });
        assertEquals(productA,service.create(ownerA,request()).id());
        verify(products).saveAndFlush(any(Product.class));
    }
    @Test void cannotReadOrUpdateAnotherShopProduct() {
        activeShops();
        notFound(() -> service.one(ownerA,productB));
        notFound(() -> service.update(ownerA,productB,request()));
        notFound(() -> service.visibility(ownerA,productB,false));
        verify(products,never()).save(any());
    }
    @Test void cannotListVariantsOrChangeStockOfAnotherShop() {
        activeShops();
        notFound(() -> service.variants(ownerA,productB));
        notFound(() -> service.addVariant(ownerA,productB,variantRequest(productB)));
        notFound(() -> service.stock(ownerA,productB,variantB,1000));
        verify(variants,never()).save(any());
    }
    @Test void variantCannotBeMovedToOtherProductViaPayload() {
        activeShops();
        Product parent=Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        ProductVariant variant=ProductVariant.builder().id(variantB).product(parent).build();
        when(products.findByIdForUpdate(productA)).thenReturn(Optional.of(parent));
        when(variants.findByIdForUpdate(variantB)).thenReturn(Optional.of(variant));
        notFound(() -> service.updateVariant(ownerA,productA,variantB,variantRequest(productB)));
        verify(variants,never()).saveAndFlush(any());
    }
    @Test void ownProductCannotUpdateForeignVariantStockEvenWhenUrlUsesOwnProduct() {
        activeShops();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        Product foreign = Product.builder().id(productB).shop(Shop.builder().id(shopB).build()).build();
        when(products.findByIdForUpdate(productA)).thenReturn(Optional.of(own));
        when(variants.findByIdForUpdate(variantB)).thenReturn(Optional.of(
            ProductVariant.builder().id(variantB).product(foreign).quantity(9).build()));
        notFound(() -> service.stock(ownerA,productA,variantB,999));
        verify(variants,never()).saveAndFlush(any());
    }
    @Test void variantRejectsImageFromOtherProduct() {
        activeShops();
        UUID imageId = UUID.randomUUID();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        Product foreign = Product.builder().id(productB).build();
        when(products.findByIdAndShopId(productA, shopA)).thenReturn(Optional.of(own));
        when(images.findById(imageId)).thenReturn(Optional.of(
            ProductImage.builder().id(imageId).product(foreign).build()));
        ProductVariantRequest r = new ProductVariantRequest(productA, "M", "Mẫu 1", "OWN-01",
                null, null, 5, "ACTIVE", imageId);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addVariant(ownerA, productA, r));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(variants, never()).saveAndFlush(any());
    }
    @Test void bulkRejectsDuplicateSkuBeforeWritingAnyRow() {
        activeShops();
        UUID img = UUID.randomUUID();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        when(products.findByIdAndShopId(productA, shopA)).thenReturn(Optional.of(own));
        when(images.findById(img)).thenReturn(Optional.of(
            ProductImage.builder().id(img).product(own).build()));
        ProductVariantRequest m = new ProductVariantRequest(productA, "M", "Mẫu 1", "DUP-SKU",
                null, null, 5, "ACTIVE", img);
        ProductVariantRequest l = new ProductVariantRequest(productA, "L", "Mẫu 1", "DUP-SKU",
                null, null, 5, "ACTIVE", img);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addVariantsBulk(ownerA, productA, List.of(m, l)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(variants, never()).saveAllAndFlush(any());
    }
    @Test void cannotRemoveAnotherShopProductOrVariant() {
        activeShops();
        notFound(() -> service.removeProductFromSale(ownerA, productB));
        notFound(() -> service.deleteVariant(ownerA, productB, variantB));
        verify(variants, never()).delete(any());
        verify(products, never()).saveAndFlush(any());
    }
    @Test void removingProductPreservesRowButHidesItFromSale() {
        activeShops();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build())
            .status("ACTIVE").build();
        when(products.findByIdForUpdate(productA)).thenReturn(Optional.of(own));
        service.removeProductFromSale(ownerA, productA);
        assertEquals("INACTIVE", own.getStatus());
        verify(products).saveAndFlush(own);
        verify(products, never()).delete(any());
    }
    @Test void variantWithoutReferencesCanBeDeleted() {
        activeShops();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        ProductVariant variant = ProductVariant.builder().id(variantB).product(own).status("ACTIVE").build();
        when(products.findByIdForUpdate(productA)).thenReturn(Optional.of(own));
        when(variants.findByIdForUpdate(variantB)).thenReturn(Optional.of(variant));
        assertTrue(service.deleteVariant(ownerA, productA, variantB));
        verify(variants).delete(variant);
    }
    @Test void variantUsedByOrderIsOnlyDeactivated() {
        activeShops();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        ProductVariant variant = ProductVariant.builder().id(variantB).product(own).status("ACTIVE").build();
        when(products.findByIdForUpdate(productA)).thenReturn(Optional.of(own));
        when(variants.findByIdForUpdate(variantB)).thenReturn(Optional.of(variant));
        when(orderItems.existsByVariantReference(variantB)).thenReturn(true);
        assertFalse(service.deleteVariant(ownerA, productA, variantB));
        assertEquals("INACTIVE", variant.getStatus());
        verify(variants).saveAndFlush(variant);
        verify(variants, never()).delete(any());
    }
    @Test void variantInCartIsOnlyDeactivated() {
        activeShops();
        Product own = Product.builder().id(productA).shop(Shop.builder().id(shopA).build()).build();
        ProductVariant variant = ProductVariant.builder().id(variantB).product(own).status("ACTIVE").build();
        when(products.findByIdForUpdate(productA)).thenReturn(Optional.of(own));
        when(variants.findByIdForUpdate(variantB)).thenReturn(Optional.of(variant));
        when(cartItems.existsByVariantId(variantB)).thenReturn(true);
        assertFalse(service.deleteVariant(ownerA, productA, variantB));
        assertEquals("INACTIVE", variant.getStatus());
        verify(variants, never()).delete(any());
    }
    @Test void suspendedShopCannotWriteEvenIfRoleStillExists() {
        when(shops.findByOwnerId(ownerA)).thenReturn(Optional.of(Shop.builder().id(shopA).status("SUSPENDED").build()));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->service.create(ownerA,request()));
        assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
        verify(products,never()).saveAndFlush(any());
    }
}
