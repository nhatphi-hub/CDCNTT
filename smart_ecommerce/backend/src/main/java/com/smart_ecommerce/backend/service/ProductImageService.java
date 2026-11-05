package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.productimage.ProductImageRequest;
import com.smart_ecommerce.backend.dto.productimage.ProductImageResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductImage;
import com.smart_ecommerce.backend.mapper.CatalogResponseMapper;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;

    public ProductImageService(
            ProductImageRepository imageRepository,
            ProductRepository productRepository
    ) {
        this.imageRepository = imageRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getByProduct(UUID productId) {
        Product product = productRepository.findVisibleCatalogById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm đang hoạt động"));
        return mapByProduct(productId, product);
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getAllByProduct(UUID productId) {
        Product product = getProduct(productId);
        return mapByProduct(productId, product);
    }

    private List<ProductImageResponse> mapByProduct(UUID productId, Product product) {
        return imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)
                .stream()
                .map(image -> toResponse(image, product))
                .toList();
    }

    @Transactional
    public ProductImageResponse create(UUID productId, ProductImageRequest request) {
        requireAdmin();
        Product product = getProduct(productId);

        boolean makePrimary = Boolean.TRUE.equals(request.isPrimary());
        if (!makePrimary && !imageRepository.existsByProductIdAndIsPrimaryTrue(productId)) {
            makePrimary = true;
        }

        if (makePrimary) {
            clearPrimary(productId);
        }

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(request.imageUrl().trim())
                .isPrimary(makePrimary)
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .build();

        return toResponse(imageRepository.save(image), product);
    }

    @Transactional
    public ProductImageResponse update(UUID imageId, ProductImageRequest request) {
        requireAdmin();
        ProductImage image = getImage(imageId);

        boolean makePrimary = Boolean.TRUE.equals(request.isPrimary());
        if (makePrimary) {
            clearPrimary(image.getProduct().getId());
        }

        image.setImageUrl(request.imageUrl().trim());
        image.setIsPrimary(makePrimary);
        image.setSortOrder(request.sortOrder() == null ? image.getSortOrder() : request.sortOrder());

        return toResponse(imageRepository.save(image), image.getProduct());
    }

    @Transactional
    public ProductImageResponse setPrimary(UUID imageId) {
        requireAdmin();
        ProductImage image = getImage(imageId);
        UUID productId = image.getProduct().getId();

        clearPrimary(productId);
        image.setIsPrimary(true);

        return toResponse(imageRepository.save(image), image.getProduct());
    }

    @Transactional
    public void delete(UUID imageId) {
        requireAdmin();
        ProductImage image = getImage(imageId);
        UUID productId = image.getProduct().getId();
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());

        imageRepository.delete(image);
        imageRepository.flush();

        if (wasPrimary) {
            List<ProductImage> remaining =
                    imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId);
            if (!remaining.isEmpty()) {
                ProductImage newPrimary = remaining.get(0);
                clearPrimary(productId);
                newPrimary.setIsPrimary(true);
                imageRepository.save(newPrimary);
            }
        }
    }

    private void clearPrimary(UUID productId) {
        imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)
                .forEach(image -> image.setIsPrimary(false));
    }

    private Product getProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));
    }

    private ProductImage getImage(UUID imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy ảnh sản phẩm"
                ));
    }

    private ProductImageResponse toResponse(ProductImage image, Product product) {
        return CatalogResponseMapper.toImage(image, product);
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!admin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ ADMIN mới được quản lý ảnh sản phẩm"
            );
        }
    }
}
