package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.productimage.ProductImageRequest;
import com.smart_ecommerce.backend.dto.productimage.ProductImageResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductImage;
import com.smart_ecommerce.backend.entity.Shop;
import com.smart_ecommerce.backend.mapper.CatalogResponseMapper;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Shop-scoped product images. Admin image endpoints remain unchanged. */
@Service
public class SellerProductImageService {
    private final ShopRepository shops;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductMediaStorage storage;

    public SellerProductImageService(ShopRepository shops, ProductRepository products,
                                     ProductImageRepository images, ProductMediaStorage storage) {
        this.shops = shops;
        this.products = products;
        this.images = images;
        this.storage = storage;
    }

    private UUID activeShopId(UUID ownerId) {
        Shop shop = shops.findByOwnerId(ownerId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa đăng ký gian hàng"));
        if (!"ACTIVE".equals(shop.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gian hàng chưa hoạt động");
        return shop.getId();
    }

    private Product owned(UUID ownerId, UUID productId, boolean lock) {
        UUID shopId = activeShopId(ownerId);
        Product product = (lock ? products.findByIdForUpdate(productId)
            : products.findByIdAndShopId(productId, shopId)).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm của shop"));
        if (product.getShop() == null || !shopId.equals(product.getShop().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm của shop");
        return product;
    }

    private ProductImage ownImage(UUID productId, UUID imageId) {
        ProductImage image = images.findById(imageId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh sản phẩm"));
        if (!productId.equals(image.getProduct().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh sản phẩm");
        return image;
    }

    private String checkedUrl(String value) {
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu URL ảnh");
        String url = value.trim();
        // Local uploaded PPee media may be stored with a relative public URL.
        // Do not accept arbitrary relative paths or traversal sequences.
        if (url.matches("^/api/media/products/[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\\.(?:jpg|png|webp)$")) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("Invalid image URL");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh phải dùng URL http/https hợp lệ");
        }
        return url;
    }

    private ProductImageResponse mapped(ProductImage image) {
        return CatalogResponseMapper.toImage(image, image.getProduct());
    }

    private void clearPrimary(UUID productId) {
        images.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)
            .forEach(image -> image.setIsPrimary(false));
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> list(UUID ownerId, UUID productId) {
        owned(ownerId, productId, false);
        return images.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)
            .stream().map(this::mapped).toList();
    }

    @Transactional
    public ProductImageResponse upload(UUID ownerId, UUID productId,
                org.springframework.web.multipart.MultipartFile file, boolean makePrimary) {
        Product product = owned(ownerId, productId, true);
        String url = storage.store(file);
        try {
            boolean primary = makePrimary || !images.existsByProductIdAndIsPrimaryTrue(productId);
            if (primary) clearPrimary(productId);
            ProductImage image = ProductImage.builder().product(product).imageUrl(url)
                    .isPrimary(primary).sortOrder(0).build();
            return mapped(images.saveAndFlush(image));
        } catch (RuntimeException e) {
            storage.removeStored(url);
            throw e;
        }
    }

    @Transactional
    public ProductImageResponse add(UUID ownerId, UUID productId, ProductImageRequest request) {
        Product product = owned(ownerId, productId, true);
        boolean primary = Boolean.TRUE.equals(request.isPrimary())
            || !images.existsByProductIdAndIsPrimaryTrue(productId);
        if (primary) clearPrimary(productId);
        ProductImage image = ProductImage.builder().product(product)
            .imageUrl(checkedUrl(request.imageUrl())).isPrimary(primary)
            .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder()).build();
        return mapped(images.saveAndFlush(image));
    }

    @Transactional
    public ProductImageResponse edit(UUID ownerId, UUID productId, UUID imageId,
                                     ProductImageRequest request) {
        owned(ownerId, productId, true);
        ProductImage image = ownImage(productId, imageId);
        image.setImageUrl(checkedUrl(request.imageUrl()));
        if (request.sortOrder() != null) image.setSortOrder(request.sortOrder());
        if (Boolean.TRUE.equals(request.isPrimary())) {
            clearPrimary(productId);
            image.setIsPrimary(true);
        }
        return mapped(images.saveAndFlush(image));
    }

    @Transactional
    public ProductImageResponse makePrimary(UUID ownerId, UUID productId, UUID imageId) {
        owned(ownerId, productId, true);
        ProductImage image = ownImage(productId, imageId);
        clearPrimary(productId);
        image.setIsPrimary(true);
        return mapped(images.saveAndFlush(image));
    }

    @Transactional
    public void remove(UUID ownerId, UUID productId, UUID imageId) {
        owned(ownerId, productId, true);
        ProductImage image = ownImage(productId, imageId);
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        images.delete(image);
        images.flush();
        if (wasPrimary) {
            List<ProductImage> remaining = images.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId);
            if (!remaining.isEmpty()) {
                clearPrimary(productId);
                remaining.get(0).setIsPrimary(true);
                images.saveAndFlush(remaining.get(0));
            }
        }
    }
}
