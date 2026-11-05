package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.productvariant.ProductVariantRequest;
import com.smart_ecommerce.backend.dto.productvariant.ProductVariantResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductVariant;
import com.smart_ecommerce.backend.entity.ProductImage;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.CartItemRepository;
import com.smart_ecommerce.backend.repository.OrderItemRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductVariantService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;

    private ProductImage checkedImage(Product product, UUID imageId) {
        if (imageId == null) return null;
        ProductImage image = imageRepository.findById(imageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy ảnh biến thể"));
        if (!image.getProduct().getId().equals(product.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh không thuộc sản phẩm");
        return image;
    }
    public ProductVariantResponse create(
            ProductVariantRequest request
    ) {

        String sku = normalizeRequiredSku(request.sku());
        String status = normalizeStatus(request.status(), ACTIVE_STATUS);
        validateQuantity(request.quantity());

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy sản phẩm")
                );

        if (variantRepository.existsBySku(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SKU Variant đã tồn tại: " + sku);
        }

        validatePrice(
                request.price(),
                request.salePrice()
        );

        ProductVariant variant =
                ProductVariant.builder()
                        .product(product)
                        .size(request.size())
                        .color(request.color())
                        .image(checkedImage(product, request.imageId()))
                        .sku(sku)
                        .price(request.price())
                        .salePrice(request.salePrice())
                        .quantity(request.quantity())
                        .status(status)
                        .build();

        return toResponse(
                variantRepository.save(variant)
        );
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getByProduct(
            UUID productId
    ) {

        Product product = getProduct(productId);
        ensurePubliclyVisible(product);

        return variantRepository
                .findByProductIdAndStatusIgnoreCase(productId, ACTIVE_STATUS)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAllByProduct(UUID productId) {
        getProduct(productId);
        return variantRepository.findByProductId(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getById(
            UUID id
    ) {

        return toResponse(getVariant(id));
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getActiveById(UUID id) {
        ProductVariant variant = variantRepository.findByIdAndStatusIgnoreCase(id, ACTIVE_STATUS)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản đang hoạt động"));
        ensurePubliclyVisible(variant.getProduct());
        return toResponse(variant);
    }

    private void ensurePubliclyVisible(Product product) {
        if (!ACTIVE_STATUS.equalsIgnoreCase(product.getStatus())
                || (product.getShop() != null
                && !"ACTIVE".equalsIgnoreCase(product.getShop().getStatus()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy sản phẩm đang hoạt động");
        }
    }

    public ProductVariantResponse update(
            UUID id,
            ProductVariantRequest request
    ) {

        String sku = normalizeRequiredSku(request.sku());

        ProductVariant variant =
                getVariant(id);

        String status = normalizeStatus(request.status(), variant.getStatus());
        validateQuantity(request.quantity());

        Product product = getProduct(request.productId());

        boolean movingToDifferentProduct = !variant.getProduct().getId().equals(product.getId());
        if (movingToDifferentProduct
                && (cartItemRepository.existsByVariantId(id)
                || orderItemRepository.existsByVariantReference(id))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể chuyển Variant đã được tham chiếu trong giỏ hàng hoặc đơn hàng");
        }

        if (!variant.getSku().equals(sku) && variantRepository.existsBySku(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SKU Variant đã tồn tại: " + sku);
        }

        validatePrice(
                request.price(),
                request.salePrice()
        );

        variant.setProduct(product);
        variant.setSize(request.size());
        variant.setColor(request.color());
        variant.setImage(checkedImage(product, request.imageId()));
        variant.setSku(sku);
        variant.setPrice(request.price());
        variant.setSalePrice(request.salePrice());
        variant.setQuantity(request.quantity());

        variant.setStatus(status);

        return toResponse(
                variantRepository.save(variant)
        );
    }

    public void delete(UUID id) {

        ProductVariant variant =
                getVariant(id);

        if (cartItemRepository.existsByVariantId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể xóa Variant đang tồn tại trong giỏ hàng");
        }

        if (orderItemRepository.existsByVariantReference(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Variant đã được dùng trong đơn hàng; hãy chuyển sang INACTIVE thay vì xóa");
        }

        variantRepository.delete(variant);
    }

    private ProductVariant getVariant(UUID id) {

        return variantRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy Product Variant")
                );
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"));
    }

    private void validatePrice(
            BigDecimal price,
            BigDecimal salePrice
    ) {

        if ((price != null && price.signum() < 0)
                || (salePrice != null && salePrice.signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá sản phẩm phải lớn hơn hoặc bằng 0");
        }

        if (price != null
                && salePrice != null
                && salePrice.compareTo(price) > 0) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá khuyến mãi không được lớn hơn giá gốc");
        }
    }

    private String normalizeRequiredSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU Variant không được để trống");
        }
        String normalized = sku.trim();
        if (normalized.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU Variant tối đa 100 ký tự");
        }
        return normalized;
    }

    private String normalizeStatus(String status, String fallback) {
        String value = status == null || status.isBlank()
                ? (fallback == null || fallback.isBlank() ? ACTIVE_STATUS : fallback)
                : status;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status Variant không hợp lệ. Chỉ chấp nhận ACTIVE hoặc INACTIVE");
        }
        return normalized;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng Variant phải lớn hơn hoặc bằng 0");
        }
    }

    private ProductVariantResponse toResponse(
            ProductVariant variant
    ) {

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
                variant.getUpdatedAt(),
                variant.getImage() == null ? null : variant.getImage().getId()
        );
    }
}
