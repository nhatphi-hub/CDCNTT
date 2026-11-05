package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.product.ProductRequest;
import com.smart_ecommerce.backend.dto.product.ProductResponse;
import com.smart_ecommerce.backend.entity.Category;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.mapper.CatalogResponseMapper;
import com.smart_ecommerce.backend.repository.CategoryRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class ProductService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> ALLOWED_GENDERS = Set.of("MEN", "WOMEN", "UNISEX", "KIDS");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;

    public ProductResponse create(ProductRequest request) {

        String sku = normalizeRequiredSku(request.sku());
        String status = resolveStatus(request.status(), ACTIVE_STATUS);
        String gender = normalizeGender(request.gender());

        if (productRepository.existsBySlug(request.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Slug đã tồn tại: " + request.slug());
        }

        if (productRepository.existsBySku(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SKU đã tồn tại: " + sku);
        }

        validateSalePrice(request.price(), request.salePrice());

        Category category = getCategory(request.categoryId());

        Product product = Product.builder()
                .name(request.name())
                .slug(request.slug())
                .sku(sku)
                .brand(request.brand())
                .description(request.description())
                .price(request.price())
                .salePrice(request.salePrice())
                .quantity(request.quantity())
                .gender(gender)
                .productType(request.productType())
                .status(status)
                .category(category)
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findVisibleCatalog()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy sản phẩm với id: " + id)
                );

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveById(UUID id) {
        Product product = productRepository.findVisibleCatalogById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm đang hoạt động với id: " + id));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getBySlug(String slug) {

        Product product = productRepository.findVisibleCatalogBySlug(slug)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy sản phẩm đang hoạt động với slug: " + slug)
                );

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getByCategory(UUID categoryId) {

        return productRepository.findVisibleCatalogByCategory(categoryId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getByStatus(String status) {

        String normalizedStatus = resolveStatus(status, null);
        return productRepository.findByStatusIgnoreCase(normalizedStatus)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(
            String keyword,
            int page,
            int size
    ) {

        Pageable pageable = createPageRequest(page, size);

        return productRepository
                .searchVisibleCatalog(keyword, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getPage(
            int page,
            int size
    ) {

        Pageable pageable = createPageRequest(page, size);

        return productRepository
                .findVisibleCatalog(pageable)
                .map(this::toResponse);
    }

    public ProductResponse update(
            UUID id,
            ProductRequest request
    ) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy sản phẩm với id: " + id)
                );

        String sku = normalizeRequiredSku(request.sku());
        String status = resolveStatus(request.status(), product.getStatus());
        String gender = normalizeGender(request.gender());

        if (!product.getSlug().equals(request.slug())
                && productRepository.existsBySlug(request.slug())) {

            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Slug đã tồn tại: " + request.slug());
        }

        if (!sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SKU đã tồn tại: " + sku);
        }

        validateSalePrice(request.price(), request.salePrice());

        Category category = getCategory(request.categoryId());

        product.setName(request.name());
        product.setSlug(request.slug());
        product.setSku(sku);
        product.setBrand(request.brand());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setSalePrice(request.salePrice());
        product.setQuantity(request.quantity());
        product.setGender(gender);
        product.setProductType(request.productType());
        product.setStatus(status);
        product.setCategory(category);

        return toResponse(productRepository.save(product));
    }

    public void delete(UUID id) {

        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy sản phẩm với id: " + id);
        }

        productRepository.deleteById(id);
    }

    private Category getCategory(UUID categoryId) {

        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy category với id: " + categoryId)
                );
    }

    private void validateSalePrice(
            BigDecimal price,
            BigDecimal salePrice
    ) {

        if (price == null || price.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá sản phẩm phải lớn hơn hoặc bằng 0");
        }
        if (salePrice != null && salePrice.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá khuyến mãi phải lớn hơn hoặc bằng 0");
        }

        if (salePrice != null &&
                salePrice.compareTo(price) > 0) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá khuyến mãi không được lớn hơn giá gốc");
        }
    }

    private String normalizeRequiredSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU không được để trống");
        }
        String normalized = sku.trim();
        if (normalized.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU tối đa 100 ký tự");
        }
        return normalized;
    }

    private String resolveStatus(String status, String fallback) {
        String value = status == null || status.isBlank()
                ? (fallback == null || fallback.isBlank() ? ACTIVE_STATUS : fallback)
                : status;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status không hợp lệ. Chỉ chấp nhận ACTIVE hoặc INACTIVE");
        }
        return normalized;
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_GENDERS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gender không hợp lệ. Chỉ chấp nhận MEN, WOMEN, UNISEX hoặc KIDS");
        }
        return normalized;
    }

    private Pageable createPageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page phải >= 0 và size phải lớn hơn 0");
        }
        return PageRequest.of(page, size);
    }

    private ProductResponse toResponse(Product product) {
        String imageUrl = productImageRepository
                .findFirstByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId())
                .map(image -> image.getImageUrl())
                .orElse(null);
        return CatalogResponseMapper.toProduct(product, imageUrl);
    }
}
