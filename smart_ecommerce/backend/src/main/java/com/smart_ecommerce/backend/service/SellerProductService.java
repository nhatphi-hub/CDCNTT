package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.product.*;
import com.smart_ecommerce.backend.dto.productvariant.*;
import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class SellerProductService {
    private final ShopRepository shops;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final CartItemRepository cartItems;
    private final OrderItemRepository orderItems;

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
    private Shop activeShop(UUID ownerId) {
        Shop shop = shops.findByOwnerId(ownerId).orElseThrow(() -> error(HttpStatus.FORBIDDEN, "Chưa đăng ký gian hàng"));
        if (!"ACTIVE".equals(shop.getStatus())) throw error(HttpStatus.FORBIDDEN, "Gian hàng chưa được phê duyệt hoặc đã bị đình chỉ");
        return shop;
    }
    private Product ownedProduct(UUID productId, UUID shopId) {
        return products.findByIdAndShopId(productId, shopId)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm của gian hàng"));
    }
    private ProductVariant ownedVariant(UUID variantId, UUID shopId) {
        return variants.findByIdAndProductShopId(variantId, shopId)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể của gian hàng"));
    }
    // Checkout locks the product before the variant. Keep the same lock order.
    private Product lockedOwnedProduct(UUID productId, UUID shopId) {
        Product p = products.findByIdForUpdate(productId)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
        if (p.getShop() == null || !shopId.equals(p.getShop().getId()))
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm của gian hàng");
        return p;
    }
    private void validatePrices(BigDecimal price, BigDecimal salePrice) {
        if (price == null || price.signum() < 0 || salePrice != null && (salePrice.signum() < 0 || salePrice.compareTo(price) > 0))
            throw error(HttpStatus.BAD_REQUEST, "Giá bán hoặc giá khuyến mãi không hợp lệ");
    }
    private String status(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(value)) throw error(HttpStatus.BAD_REQUEST, "Trạng thái chỉ nhận ACTIVE/INACTIVE");
        return value;
    }
    private String gender(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MEN", "WOMEN", "UNISEX", "KIDS").contains(result)) throw error(HttpStatus.BAD_REQUEST, "Giới tính sản phẩm không hợp lệ");
        return result;
    }
    private Category category(UUID id) {
        return id == null ? null : categories.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"));
    }
    private void checkQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) throw error(HttpStatus.BAD_REQUEST, "Số lượng không hợp lệ");
    }
    private String sku(String s) {
        if (s == null || s.isBlank() || s.trim().length() > 100) throw error(HttpStatus.BAD_REQUEST, "SKU không hợp lệ");
        return s.trim();
    }
    private String slug(String s) {
        if (s == null || s.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Slug không hợp lệ");
        return s.trim();
    }
    private ProductResponse map(Product p) {
        return new ProductResponse(p.getId(), p.getCategory()==null?null:p.getCategory().getId(),
            p.getCategory()==null?null:p.getCategory().getName(),p.getName(),p.getSlug(),p.getSku(),
            p.getBrand(),p.getDescription(),p.getPrice(),p.getSalePrice(),p.getQuantity(),
            p.getGender(),p.getProductType(),p.getStatus(),p.getCreatedAt(),p.getUpdatedAt());
    }
    private ProductVariantResponse map(ProductVariant v) {
        return new ProductVariantResponse(v.getId(),v.getProduct().getId(),v.getProduct().getName(),
            v.getSize(),v.getColor(),v.getSku(),v.getPrice(),v.getSalePrice(),v.getQuantity(),
            v.getStatus(),v.getCreatedAt(),v.getUpdatedAt(),v.getImage()==null?null:v.getImage().getId());
    }
    @Transactional(readOnly=true)
    public List<ProductResponse> list(UUID ownerId) {
        Shop shop = activeShop(ownerId);
        return products.findByShopIdOrderByCreatedAtDesc(shop.getId()).stream().map(this::map).toList();
    }
    @Transactional(readOnly=true)
    public ProductResponse one(UUID ownerId, UUID id) {
        return map(ownedProduct(id,activeShop(ownerId).getId()));
    }
    public ProductResponse create(UUID ownerId, ProductRequest r) {
        Shop shop=activeShop(ownerId);
        validatePrices(r.price(),r.salePrice()); checkQuantity(r.quantity());
        String idSku=sku(r.sku()), idSlug=slug(r.slug());
        if (products.existsBySku(idSku) || products.existsBySlug(idSlug)) throw error(HttpStatus.CONFLICT,"SKU hoặc slug đã tồn tại");
        Product p=Product.builder().shop(shop).name(r.name()).slug(idSlug).sku(idSku)
            .brand(r.brand()).description(r.description()).category(category(r.categoryId()))
            .price(r.price()).salePrice(r.salePrice()).quantity(r.quantity()).gender(gender(r.gender()))
            .productType(r.productType()).status(status(r.status(),"ACTIVE")).build();
        return map(products.saveAndFlush(p));
    }
    public ProductResponse update(UUID ownerId, UUID id, ProductRequest r) {
        Product p=lockedOwnedProduct(id,activeShop(ownerId).getId());
        validatePrices(r.price(),r.salePrice()); checkQuantity(r.quantity());
        String idSku=sku(r.sku()),idSlug=slug(r.slug());
        if (!p.getSku().equals(idSku)&&products.existsBySku(idSku) || !p.getSlug().equals(idSlug)&&products.existsBySlug(idSlug))
            throw error(HttpStatus.CONFLICT,"SKU hoặc slug đã tồn tại");
        p.setName(r.name());p.setSku(idSku);p.setSlug(idSlug);p.setBrand(r.brand());p.setDescription(r.description());
        p.setCategory(category(r.categoryId()));p.setPrice(r.price());p.setSalePrice(r.salePrice());
        p.setQuantity(r.quantity());p.setGender(gender(r.gender()));p.setProductType(r.productType());
        p.setStatus(status(r.status(),p.getStatus()));
        return map(products.saveAndFlush(p));
    }
    public ProductResponse visibility(UUID ownerId,UUID id,boolean visible) {
        Product p=lockedOwnedProduct(id,activeShop(ownerId).getId());
        p.setStatus(visible?"ACTIVE":"INACTIVE");
        return map(products.saveAndFlush(p));
    }
    /** Keep the historical product row for orders/reviews/returns and foreign keys.
     * The public catalog and checkout only accept ACTIVE products.
     * In particular, a prohibited item must disappear immediately from sale.
     */
    public void removeProductFromSale(UUID ownerId, UUID productId) {
        Product p = lockedOwnedProduct(productId, activeShop(ownerId).getId());
        p.setStatus("INACTIVE");
        products.saveAndFlush(p);
    }
    @Transactional(readOnly=true)
    public List<ProductVariantResponse> variants(UUID ownerId, UUID productId) {
        ownedProduct(productId,activeShop(ownerId).getId());
        return this.variants.findByProductId(productId).stream().map(this::map).toList();
    }
    // Images may only be associated with variants of the SAME owned product.
    private ProductImage variantImage(UUID productId, UUID imageId) {
        if (imageId == null) return null;
        ProductImage image = images.findById(imageId)
            .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Không tìm thấy ảnh của mẫu"));
        if (!image.getProduct().getId().equals(productId))
            throw error(HttpStatus.BAD_REQUEST, "Ảnh không thuộc sản phẩm đang chỉnh sửa");
        return image;
    }
    public ProductVariantResponse addVariant(UUID ownerId,UUID productId,ProductVariantRequest r) {
        Product p=ownedProduct(productId,activeShop(ownerId).getId());
        if (!productId.equals(r.productId())) throw error(HttpStatus.BAD_REQUEST,"productId không khớp đường dẫn");
        checkQuantity(r.quantity());String idSku=sku(r.sku());
        if (variants.existsBySku(idSku)) throw error(HttpStatus.CONFLICT,"SKU biến thể đã tồn tại");
        if (r.price()!=null) validatePrices(r.price(),r.salePrice());
        else if (r.salePrice()!=null) throw error(HttpStatus.BAD_REQUEST,"Không thể có giá khuyến mãi khi giá trống");
        ProductVariant v=ProductVariant.builder().product(p).sku(idSku).size(r.size()).color(r.color())
            .price(r.price()).salePrice(r.salePrice()).quantity(r.quantity()).image(variantImage(productId,r.imageId()))
            .status(status(r.status(),"ACTIVE")).build();
        return map(variants.saveAndFlush(v));
    }
    public ProductVariantResponse updateVariant(UUID ownerId,UUID productId,UUID id,ProductVariantRequest r) {
        Product p=lockedOwnedProduct(productId,activeShop(ownerId).getId());
        ProductVariant v=variants.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy biến thể"));
        if (!p.getId().equals(v.getProduct().getId())) throw error(HttpStatus.NOT_FOUND,"Biến thể không thuộc sản phẩm");
        if (!v.getProduct().getId().equals(productId) || !r.productId().equals(productId))
            throw error(HttpStatus.NOT_FOUND,"Biến thể không thuộc sản phẩm");
        String idSku=sku(r.sku());checkQuantity(r.quantity());
        if (!v.getSku().equals(idSku)&&variants.existsBySku(idSku)) throw error(HttpStatus.CONFLICT,"SKU biến thể đã tồn tại");
        if (r.price()!=null) validatePrices(r.price(),r.salePrice());
        else if (r.salePrice()!=null) throw error(HttpStatus.BAD_REQUEST,"Không thể có giá khuyến mãi khi giá trống");
        v.setSku(idSku);v.setSize(r.size());v.setColor(r.color());v.setPrice(r.price());v.setSalePrice(r.salePrice());
        v.setQuantity(r.quantity());v.setImage(variantImage(productId,r.imageId()));
        v.setStatus(status(r.status(),v.getStatus()));
        return map(variants.saveAndFlush(v));
    }
    // Atomic matrix creation: one invalid SKU/image/size rolls back the entire batch.
    public List<ProductVariantResponse> addVariantsBulk(UUID ownerId, UUID productId, List<ProductVariantRequest> requests) {
        Product p = ownedProduct(productId, activeShop(ownerId).getId());
        if (requests == null || requests.isEmpty() || requests.size() > 100)
            throw error(HttpStatus.BAD_REQUEST, "Chỉ được tạo từ 1 đến 100 biến thể mỗi lượt");
        Set<String> skuSet = new HashSet<>();
        Set<String> combinationSet = new HashSet<>();
        for (ProductVariant v : variants.findByProductId(productId)) {
            String imageKey = v.getImage() == null ? "" : v.getImage().getId().toString();
            combinationSet.add(imageKey + ":" + String.valueOf(v.getSize()).trim().toLowerCase(Locale.ROOT));
        }
        List<ProductVariant> pending = new ArrayList<>();
        for (ProductVariantRequest r : requests) {
            if (!productId.equals(r.productId())) throw error(HttpStatus.BAD_REQUEST, "productId không khớp");
            checkQuantity(r.quantity());
            String idSku = sku(r.sku());
            if (!skuSet.add(idSku) || variants.existsBySku(idSku))
                throw error(HttpStatus.CONFLICT, "SKU biến thể đã tồn tại: " + idSku);
            if (r.price() != null) validatePrices(r.price(),r.salePrice());
            else if (r.salePrice() != null) throw error(HttpStatus.BAD_REQUEST, "Cần nhập giá gốc trước giá khuyến mãi");
            ProductImage image = variantImage(productId, r.imageId());
            if (image == null) throw error(HttpStatus.BAD_REQUEST, "Hãy chọn ảnh cho mỗi mẫu");
            if (r.size() == null || r.size().isBlank()) throw error(HttpStatus.BAD_REQUEST, "Thiếu kích cỡ");
            String combination = image.getId() + ":" + r.size().trim().toLowerCase(Locale.ROOT);
            if (!combinationSet.add(combination))
                throw error(HttpStatus.CONFLICT, "Mẫu ảnh và kích cỡ này đã tồn tại");
            pending.add(ProductVariant.builder().product(p).image(image).sku(idSku)
                .size(r.size().trim()).color(r.color()).price(r.price()).salePrice(r.salePrice())
                .quantity(r.quantity()).status(status(r.status(), "ACTIVE")).build());
        }
        return variants.saveAllAndFlush(pending).stream().map(this::map).toList();
    }
    /** A variant that has ever been purchased or is in a cart cannot be hard
     * deleted without breaking references. In that case deactivate it instead.
     * All ownership checks and locks are seller-scoped.
     * @return true if physically deleted; false if safely retired.
     */
    public boolean deleteVariant(UUID ownerId, UUID productId, UUID variantId) {
        Product product = lockedOwnedProduct(productId, activeShop(ownerId).getId());
        ProductVariant variant = variants.findByIdForUpdate(variantId)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể"));
        if (!product.getId().equals(variant.getProduct().getId()))
            throw error(HttpStatus.NOT_FOUND, "Biến thể không thuộc sản phẩm của gian hàng");
        if (orderItems.existsByVariantReference(variantId) || cartItems.existsByVariantId(variantId)) {
            variant.setStatus("INACTIVE");
            variants.saveAndFlush(variant);
            return false;
        }
        variants.delete(variant);
        variants.flush();
        return true;
    }

    public ProductVariantResponse stock(UUID ownerId,UUID productId,UUID id,Integer quantity) {
        Product p=lockedOwnedProduct(productId,activeShop(ownerId).getId());
        checkQuantity(quantity);
        // OrderService uses the same pessimistic row lock when deducting inventory.
        ProductVariant v=variants.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy biến thể"));
        if (!v.getProduct().getId().equals(productId) || !p.getId().equals(productId))
            throw error(HttpStatus.NOT_FOUND,"Biến thể không thuộc sản phẩm");
        v.setQuantity(quantity);
        return map(variants.saveAndFlush(v));
    }
}
