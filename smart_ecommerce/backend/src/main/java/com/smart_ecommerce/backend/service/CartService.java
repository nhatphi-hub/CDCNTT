package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.cart.AddCartItemRequest;
import com.smart_ecommerce.backend.dto.cart.CartItemResponse;
import com.smart_ecommerce.backend.dto.cart.CartResponse;
import com.smart_ecommerce.backend.dto.cart.UpdateCartItemRequest;
import com.smart_ecommerce.backend.entity.Cart;
import com.smart_ecommerce.backend.entity.CartItem;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductVariant;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.CartItemRepository;
import com.smart_ecommerce.backend.repository.CartRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.ProductVariantRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public Cart getOrCreateCart(UUID userId) {

        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {

                    User user = userRepository
                            .findById(userId)
                            .orElseThrow(() ->
                                    new ResponseStatusException(HttpStatus.NOT_FOUND,
                                            "Không tìm thấy người dùng")
                            );

                    Cart cart = Cart.builder()
                            .user(user)
                            .build();

                    return cartRepository.save(cart);
                });
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {

        Cart cart = cartRepository
                .findByUserId(userId)
                .orElse(null);

        if (cart == null) {

            return new CartResponse(
                    null,
                    userId,
                    List.of(),
                    0,
                    BigDecimal.ZERO
            );
        }

        return toResponse(cart);
    }

    public CartResponse addItem(
            UUID userId,
            AddCartItemRequest request
    ) {

        validateRequestedQuantity(request.quantity());

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Không tìm thấy sản phẩm")
                );

        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())
                || (product.getShop() != null
                && !"ACTIVE".equalsIgnoreCase(product.getShop().getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sản phẩm hoặc gian hàng hiện không hoạt động");
        }

        boolean productHasVariants = productVariantRepository.existsByProductId(product.getId());
        if (request.variantId() == null && productHasVariants) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn phiên bản sản phẩm");
        }

        ProductVariant variant = null;

        /*
         * Nếu request có variantId,
         * kiểm tra Variant tồn tại và thuộc đúng Product.
         */
        if (request.variantId() != null) {

            variant = productVariantRepository
                    .findById(request.variantId())
                    .orElseThrow(() ->
                            new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Không tìm thấy Product Variant")
                    );

            if (!variant.getProduct()
                    .getId()
                    .equals(product.getId())) {

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Variant không thuộc sản phẩm này");
            }

            if (!"ACTIVE".equalsIgnoreCase(
                    variant.getStatus()
            )) {

                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Product Variant hiện không hoạt động");
            }
        }

        Cart cart = getOrCreateCart(userId);

        Integer availableQuantity;

        if (variant != null) {
            availableQuantity = variant.getQuantity();
        } else {
            availableQuantity = product.getQuantity();
        }

        validateStock(
                availableQuantity,
                request.quantity()
        );

        CartItem item =
                cartItemRepository
                        .findByCartIdAndProductIdAndVariantId(
                                cart.getId(),
                                product.getId(),
                                request.variantId()
                        )
                        .orElse(null);

        if (item == null) {

            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .quantity(request.quantity())
                    .build();
            cart.getItems().add(item);

        } else {

            int newQuantity =
                    item.getQuantity()
                            + request.quantity();

            validateStock(
                    availableQuantity,
                    newQuantity
            );

            item.setQuantity(newQuantity);
        }

        cartItemRepository.save(item);

        return getCart(userId);
    }

    public CartResponse updateItem(
            UUID userId,
            UUID itemId,
            UpdateCartItemRequest request
    ) {

        CartItem item =
                getUserCartItem(
                        userId,
                        itemId
                );

        ProductVariant variant =
                item.getVariant();

        Product product = item.getProduct();
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())
                || (product.getShop() != null
                && !"ACTIVE".equalsIgnoreCase(product.getShop().getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sản phẩm hoặc gian hàng hiện không hoạt động");
        }

        if (variant == null && productVariantRepository.existsByProductId(product.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn phiên bản sản phẩm");
        }

        Integer availableQuantity;

        if (variant != null) {

            if (!product.getId().equals(variant.getProduct().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phiên bản trong giỏ hàng không còn khớp sản phẩm");
            }

            if (!"ACTIVE".equalsIgnoreCase(
                    variant.getStatus()
            )) {

                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Product Variant hiện không hoạt động");
            }

            availableQuantity =
                    variant.getQuantity();

        } else {

            availableQuantity =
                    item.getProduct().getQuantity();
        }

        validateStock(
                availableQuantity,
                request.quantity()
        );

        item.setQuantity(
                request.quantity()
        );

        cartItemRepository.save(item);

        return getCart(userId);
    }

    public CartResponse removeItem(
            UUID userId,
            UUID itemId
    ) {

        CartItem item =
                getUserCartItem(
                        userId,
                        itemId
                );

        Cart cart = item.getCart();

        cart.getItems().remove(item);

        cartItemRepository.delete(item);

        return toResponse(cart);
    }

    public void clearCart(UUID userId) {

        Cart cart = cartRepository
                .findByUserId(userId)
                .orElse(null);

        if (cart == null) {
            return;
        }

        cartRepository.delete(cart);
    }

    private void validateStock(
            Integer availableQuantity,
            Integer requestedQuantity
    ) {

        if (requestedQuantity == null
                || requestedQuantity <= 0) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng phải lớn hơn 0");
        }

        if (availableQuantity == null
                || availableQuantity <= 0) {

            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sản phẩm đã hết hàng");
        }

        if (requestedQuantity > availableQuantity) {

            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số lượng vượt quá tồn kho. Tồn kho hiện tại: " + availableQuantity);
        }
    }

    private void validateRequestedQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng phải lớn hơn 0");
        }
    }

    private CartItem getUserCartItem(
            UUID userId,
            UUID itemId
    ) {

        return cartItemRepository
                .findByIdAndCartUserId(
                        itemId,
                        userId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm trong giỏ hàng"));
    }

    private CartResponse toResponse(
            Cart cart
    ) {

        List<CartItemResponse> items =
                cart.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList();

        int totalQuantity =
                items.stream()
                        .mapToInt(
                                CartItemResponse::quantity
                        )
                        .sum();

        BigDecimal totalAmount =
                items.stream()
                        .map(
                                CartItemResponse::subtotal
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return new CartResponse(
                cart.getId(),
                cart.getUser().getId(),
                items,
                totalQuantity,
                totalAmount
        );
    }

    private CartItemResponse toItemResponse(
            CartItem item
    ) {

        Product product =
                item.getProduct();

        ProductVariant variant =
                item.getVariant();

        BigDecimal unitPrice =
                getSellingPrice(
                        product,
                        variant
                );

        BigDecimal subtotal =
                unitPrice.multiply(
                        BigDecimal.valueOf(
                                item.getQuantity()
                        )
                );

        Integer availableQuantity;

        if (variant != null) {
            availableQuantity =
                    variant.getQuantity();
        } else {
            availableQuantity =
                    product.getQuantity();
        }

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                variant != null && variant.getImage() != null
                        ? variant.getImage().getImageUrl()
                        : productImageRepository.findFirstByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId())
                            .map(image -> image.getImageUrl()).orElse(null),
                unitPrice,
                item.getQuantity(),
                subtotal,
                availableQuantity,
                variant != null
                        ? variant.getId()
                        : null,
                product.getShop() == null ? "PPee" : product.getShop().getName(),
                variant == null ? null
                        : java.util.stream.Stream.of(variant.getColor(), variant.getSize())
                            .filter(value -> value != null && !value.isBlank())
                            .collect(java.util.stream.Collectors.joining(" / "))
        );
    }

    private BigDecimal getSellingPrice(
            Product product,
            ProductVariant variant
    ) {

        /*
         * Nếu CartItem có Variant,
         * ưu tiên giá của Variant.
         */
        if (variant != null) {

            if (variant.getSalePrice() != null
                    && variant.getPrice() != null
                    && variant.getSalePrice()
                    .compareTo(
                            variant.getPrice()
                    ) < 0) {

                return variant.getSalePrice();
            }

            if (variant.getPrice() != null) {
                return variant.getPrice();
            }
        }

        /*
         * Nếu không có Variant,
         * dùng giá của Product.
         */
        if (product.getSalePrice() != null
                && product.getSalePrice()
                .compareTo(
                        product.getPrice()
                ) < 0) {

            return product.getSalePrice();
        }

        return product.getPrice();
    }
}
