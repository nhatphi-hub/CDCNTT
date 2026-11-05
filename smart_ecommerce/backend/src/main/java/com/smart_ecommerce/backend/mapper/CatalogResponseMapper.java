package com.smart_ecommerce.backend.mapper;

import com.smart_ecommerce.backend.dto.product.ProductResponse;
import com.smart_ecommerce.backend.dto.productimage.ProductImageResponse;
import com.smart_ecommerce.backend.dto.productvariant.ProductVariantResponse;
import com.smart_ecommerce.backend.entity.Category;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductImage;
import com.smart_ecommerce.backend.entity.ProductVariant;

/** Maps catalog entities only; callers retain authorization and database access. */
public final class CatalogResponseMapper {

    private CatalogResponseMapper() {
    }

    public static ProductResponse toProduct(Product product, String imageUrl) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getName(),
                product.getSlug(),
                product.getSku(),
                product.getBrand(),
                product.getDescription(),
                product.getPrice(),
                product.getSalePrice(),
                product.getQuantity(),
                product.getGender(),
                product.getProductType(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                imageUrl
        );
    }

    public static ProductVariantResponse toVariant(ProductVariant variant) {
        Product product = variant.getProduct();
        return new ProductVariantResponse(
                variant.getId(),
                product.getId(),
                product.getName(),
                variant.getSize(),
                variant.getColor(),
                variant.getSku(),
                variant.getPrice(),
                variant.getSalePrice(),
                variant.getQuantity(),
                variant.getStatus(),
                variant.getCreatedAt(),
                variant.getUpdatedAt()
        );
    }

    public static ProductImageResponse toImage(ProductImage image, Product product) {
        return new ProductImageResponse(
                image.getId(),
                product.getId(),
                product.getName(),
                image.getImageUrl(),
                image.getIsPrimary(),
                image.getSortOrder()
        );
    }
}
