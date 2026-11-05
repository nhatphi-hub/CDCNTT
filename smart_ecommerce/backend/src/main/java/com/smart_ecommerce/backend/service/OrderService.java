package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.coupon.CouponApplication;
import com.smart_ecommerce.backend.dto.order.CreateOrderRequest;
import com.smart_ecommerce.backend.dto.order.OrderItemResponse;
import com.smart_ecommerce.backend.dto.order.OrderResponse;
import com.smart_ecommerce.backend.entity.Address;
import com.smart_ecommerce.backend.entity.Cart;
import com.smart_ecommerce.backend.entity.CartItem;
import com.smart_ecommerce.backend.entity.Coupon;
import com.smart_ecommerce.backend.entity.Order;
import com.smart_ecommerce.backend.entity.OrderItem;
import com.smart_ecommerce.backend.entity.ShopOrder;
import com.smart_ecommerce.backend.repository.ShopOrderRepository;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.ProductVariant;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.AddressRepository;
import com.smart_ecommerce.backend.repository.CartRepository;
import com.smart_ecommerce.backend.repository.CouponRepository;
import com.smart_ecommerce.backend.repository.OrderRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.ProductVariantRepository;
import com.smart_ecommerce.backend.repository.ShippingRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final List<String> PAYMENT_METHODS =
            List.of("COD", "BANK_TRANSFER", "VNPAY", "MOMO");
    private static final List<String> ALLOWED_STATUSES =
            List.of("PENDING", "CONFIRMED", "SHIPPING", "DELIVERED", "CANCELLED");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final PaymentService paymentService;
    private final ShippingRepository shippingRepository;
    private final ShopOrderRepository shopOrderRepository;

    @Value("${app.order.shipping-fee:0.00}")
    private BigDecimal configuredShippingFee;

    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

        Address address = addressRepository
                .findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ"));

        String paymentMethod = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_METHODS.contains(paymentMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phương thức thanh toán không hợp lệ. Cho phép: COD, BANK_TRANSFER, VNPAY, MOMO");
        }

        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Giỏ hàng đang trống"));
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Giỏ hàng đang trống");
        }

        Set<UUID> selectedIds = request.selectedCartItemIds() == null
                ? null : new HashSet<>(request.selectedCartItemIds());
        if (selectedIds != null && (selectedIds.isEmpty()
                || selectedIds.size() != request.selectedCartItemIds().size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Danh sách sản phẩm được chọn không hợp lệ");
        }
        List<CartItem> selectedItems = selectedIds == null
                ? List.copyOf(cart.getItems())
                : cart.getItems().stream()
                    .filter(item -> selectedIds.contains(item.getId()))
                    .toList();
        if (selectedItems.isEmpty() || (selectedIds != null
                && selectedItems.size() != selectedIds.size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Một số sản phẩm đã chọn không tồn tại trong giỏ hàng của bạn");
        }

        Map<UUID, Product> lockedProducts = lockProducts(selectedItems);
        Map<UUID, ProductVariant> lockedVariants = lockVariants(selectedItems);
        List<LineSelection> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);

        for (CartItem cartItem : selectedItems) {
            UUID productId = cartItem.getProduct().getId();
            Product product = lockedProducts.get(productId);
            if (product == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm trong giỏ hàng");
            }
            if (product.getShop() != null && !"ACTIVE".equalsIgnoreCase(product.getShop().getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop của sản phẩm chưa hoạt động");
            }
            if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Sản phẩm hiện không hoạt động: " + product.getName());
            }

            UUID variantId = cartItem.getVariant() == null
                    ? null
                    : cartItem.getVariant().getId();
            ProductVariant variant = variantId == null
                    ? null
                    : lockedVariants.get(variantId);

            if (variantId != null && variant == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Phiên bản sản phẩm trong giỏ hàng không còn tồn tại");
            }
            if (variant != null && !productId.equals(variant.getProduct().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phiên bản sản phẩm không thuộc về sản phẩm trong giỏ hàng");
            }
            if (variant == null && productVariantRepository.existsByProductId(productId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn phiên bản cho sản phẩm: " + product.getName());
            }
            if (variant != null && !"ACTIVE".equalsIgnoreCase(variant.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Phiên bản sản phẩm hiện không hoạt động: " + product.getName());
            }

            Integer availableQuantity = variant == null
                    ? product.getQuantity()
                    : variant.getQuantity();
            Integer requestedQuantity = cartItem.getQuantity();
            if (requestedQuantity == null || requestedQuantity <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Số lượng sản phẩm phải lớn hơn 0");
            }
            if (availableQuantity == null || requestedQuantity > availableQuantity) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Số lượng sản phẩm không đủ tồn kho: " + product.getName()
                                + ". Tồn kho hiện tại: " + availableQuantity);
            }

            BigDecimal unitPrice = getSellingPrice(product, variant);
            BigDecimal lineSubtotal = unitPrice
                    .multiply(BigDecimal.valueOf(requestedQuantity))
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(new LineSelection(product, variant, requestedQuantity, unitPrice, lineSubtotal));
            subtotal = subtotal.add(lineSubtotal);
        }

        BigDecimal shippingFee = getShippingFee();
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);
        Coupon coupon = null;
        String couponCodeSnapshot = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            Map<UUID, BigDecimal> shopSubtotals = new HashMap<>();
            for (LineSelection line : lines) {
                if (line.product().getShop() != null) {
                    shopSubtotals.merge(line.product().getShop().getId(),
                            line.subtotal(), BigDecimal::add);
                }
            }
            CouponApplication application = couponService.applyToOrder(
                    request.couponCode(), subtotal, shopSubtotals);
            coupon = couponRepository.getReferenceById(application.couponId());
            couponCodeSnapshot = application.code();
            discountAmount = application.discountAmount();
        }

        BigDecimal totalAmount = subtotal
                .subtract(discountAmount)
                .add(shippingFee)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .user(user)
                .address(address)
                .coupon(coupon)
                .couponCodeSnapshot(couponCodeSnapshot)
                .shippingRecipientName(address.getRecipientName())
                .shippingPhone(address.getPhone())
                .shippingProvince(address.getProvince())
                .shippingDistrict(address.getDistrict())
                .shippingWard(address.getWard())
                .shippingAddressDetail(address.getAddressDetail())
                .orderCode(generateOrderCode())
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .status("PENDING")
                .paymentMethod(paymentMethod)
                .paymentStatus("UNPAID")
                .note(request.note())
                .build();

        for (LineSelection line : lines) {
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(line.product())
                    .shop(line.product().getShop())
                    .variant(line.variant())
                    .variantIdSnapshot(line.variant() == null ? null : line.variant().getId())
                    .inventoryUsesVariant(line.variant() != null)
                    .productNameSnapshot(line.product().getName())
                    .variantNameSnapshot(getVariantName(line.variant()))
                    .skuSnapshot(line.variant() == null
                            ? line.product().getSku()
                            : line.variant().getSku())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .subtotal(line.subtotal())
                    .build();
            order.getItems().add(item);

            if (line.variant() == null) {
                line.product().setQuantity(line.product().getQuantity() - line.quantity());
            } else {
                line.variant().setQuantity(line.variant().getQuantity() - line.quantity());
            }
        }

        Order savedOrder = orderRepository.saveAndFlush(order);
        order.getItems().stream().map(OrderItem::getShop).filter(java.util.Objects::nonNull)
            .collect(Collectors.toMap(s -> s.getId(), s -> s, (a, b) -> a))
            .values().forEach(shop -> shopOrderRepository.save(ShopOrder.builder()
                .order(savedOrder).shop(shop).status("PENDING").build()));
        paymentService.recordCodPayment(savedOrder);

        // Preserve non-selected cart lines after the order is committed.
        cart.getItems().removeIf(item -> selectedIds == null
                || selectedIds.contains(item.getId()));

        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(UUID userId, UUID orderId) {
        return toResponse(orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng")));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForManagement() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForManagement(UUID orderId) {
        return toResponse(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng")));
    }

    public OrderResponse cancel(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!"PENDING".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể hủy đơn hàng đang ở trạng thái PENDING");
        }
        rejectCancellationOfPaidOrder(order);

        restoreStock(order);
        order.setStatus("CANCELLED");
        shopOrderRepository.findByOrder_Id(orderId).forEach(so -> so.setStatus("CANCELLED"));
        paymentService.cancelPendingPayments(order);
        return toResponse(order);
    }

    public OrderResponse updateStatus(UUID orderId, String requestedStatus) {
        String newStatus = requestedStatus == null
                ? ""
                : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Cho phép: " + String.join(", ", ALLOWED_STATUSES));
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!isAllowedTransition(order.getStatus(), newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể chuyển trạng thái đơn hàng từ "
                            + order.getStatus() + " sang " + newStatus);
        }

        if ("CANCELLED".equals(newStatus)) {
            rejectCancellationOfPaidOrder(order);
            if (shippingRepository.existsByOrder_Id(orderId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Không thể hủy đơn hàng đã có vận đơn; cần xử lý vận đơn trước");
            }
            restoreStock(order);
            paymentService.cancelPendingPayments(order);
        }
        if ("SHIPPING".equals(newStatus) && shopOrderRepository.findByOrder_Id(orderId)
                .stream().anyMatch(so -> !"READY".equals(so.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mọi gian hàng phải chuẩn bị xong trước khi giao đơn");
        }
        if ("SHIPPING".equals(newStatus)
                && !"COD".equalsIgnoreCase(order.getPaymentMethod())
                && !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn thanh toán trực tuyến phải được thanh toán trước khi chuyển sang giao hàng");
        }
        if ("DELIVERED".equals(newStatus)) {
            paymentService.completeCodPayment(order);
        }
        order.setStatus(newStatus);
        if ("SHIPPING".equals(newStatus) || "DELIVERED".equals(newStatus)
                || "CANCELLED".equals(newStatus)) {
            shopOrderRepository.findByOrder_Id(orderId).forEach(so -> so.setStatus(newStatus));
        }
        return toResponse(order);
    }

    private void rejectCancellationOfPaidOrder(Order order) {
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng đã thanh toán; cần hoàn tiền trước khi hủy");
        }
    }

    private Map<UUID, Product> lockProducts(List<CartItem> items) {
        Set<UUID> productIds = items.stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Product> locked = new HashMap<>();
        productIds.stream().sorted(Comparator.comparing(UUID::toString)).forEach(id ->
                productRepository.findByIdForUpdate(id)
                        .ifPresent(product -> locked.put(id, product)));
        return locked;
    }

    private Map<UUID, ProductVariant> lockVariants(List<CartItem> items) {
        Set<UUID> variantIds = items.stream()
                .filter(item -> item.getVariant() != null)
                .map(item -> item.getVariant().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, ProductVariant> locked = new HashMap<>();
        variantIds.stream().sorted(Comparator.comparing(UUID::toString)).forEach(id ->
                productVariantRepository.findByIdForUpdate(id)
                        .ifPresent(variant -> locked.put(id, variant)));
        return locked;
    }

    private void restoreStock(Order order) {
        List<OrderItem> items = order.getItems();
        Map<UUID, Product> lockedProducts = new HashMap<>();
        items.stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> productRepository.findByIdForUpdate(id)
                        .ifPresent(product -> lockedProducts.put(id, product)));

        Map<UUID, ProductVariant> lockedVariants = new HashMap<>();
        items.stream()
                .filter(this::usesVariantInventory)
                .map(this::getInventoryVariantId)
                .filter(id -> id != null)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> productVariantRepository.findByIdForUpdate(id)
                        .ifPresent(variant -> lockedVariants.put(id, variant)));

        for (OrderItem item : items) {
            UUID productId = item.getProduct().getId();
            Product product = lockedProducts.get(productId);
            if (product == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không thể hoàn tồn kho vì sản phẩm không còn tồn tại");
            }

            if (usesVariantInventory(item)) {
                UUID variantId = getInventoryVariantId(item);
                ProductVariant variant = variantId == null ? null : lockedVariants.get(variantId);
                if (variant == null) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Không thể hoàn tồn kho vì phiên bản của đơn hàng không còn tồn tại");
                }
                if (!productId.equals(variant.getProduct().getId())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Không thể hoàn tồn kho vì phiên bản không khớp sản phẩm");
                }
                variant.setQuantity(variant.getQuantity() + item.getQuantity());
            } else {
                product.setQuantity(product.getQuantity() + item.getQuantity());
            }
        }
    }

    private boolean usesVariantInventory(OrderItem item) {
        return Boolean.TRUE.equals(item.getInventoryUsesVariant())
                || item.getVariantIdSnapshot() != null
                || item.getVariant() != null;
    }

    private UUID getInventoryVariantId(OrderItem item) {
        if (item.getVariantIdSnapshot() != null) {
            return item.getVariantIdSnapshot();
        }
        return item.getVariant() == null ? null : item.getVariant().getId();
    }

    private boolean isAllowedTransition(String currentStatus, String newStatus) {
        if ("PENDING".equals(currentStatus)) {
            return List.of("CONFIRMED", "CANCELLED").contains(newStatus);
        }
        if ("CONFIRMED".equals(currentStatus)) {
            return List.of("SHIPPING", "CANCELLED").contains(newStatus);
        }
        if ("SHIPPING".equals(currentStatus)) {
            return "DELIVERED".equals(newStatus);
        }
        return false;
    }

    private BigDecimal getShippingFee() {
        if (configuredShippingFee == null || configuredShippingFee.signum() < 0) {
            throw new IllegalStateException("app.order.shipping-fee phải lớn hơn hoặc bằng 0");
        }
        return configuredShippingFee.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateOrderCode() {
        String prefix = "ORD-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-";
        String code;
        do {
            code = prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    private BigDecimal getSellingPrice(Product product, ProductVariant variant) {
        BigDecimal price = null;
        if (variant != null) {
            if (variant.getSalePrice() != null
                    && variant.getPrice() != null
                    && variant.getSalePrice().compareTo(variant.getPrice()) < 0) {
                price = variant.getSalePrice();
            } else if (variant.getPrice() != null) {
                price = variant.getPrice();
            }
        }

        if (price == null) {
            if (product.getSalePrice() != null
                    && product.getPrice() != null
                    && product.getSalePrice().compareTo(product.getPrice()) < 0) {
                price = product.getSalePrice();
            } else {
                price = product.getPrice();
            }
        }
        if (price == null || price.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm chưa có giá bán hợp lệ: " + product.getName());
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String getVariantName(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        String name = java.util.stream.Stream.of(variant.getSize(), variant.getColor())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" / "));
        return name.isBlank() ? null : name;
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .addressId(order.getAddress() == null ? null : order.getAddress().getId())
                .orderCode(order.getOrderCode())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .couponId(order.getCoupon() == null ? null : order.getCoupon().getId())
                .couponCodeSnapshot(order.getCouponCodeSnapshot())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingProvince(order.getShippingProvince())
                .shippingDistrict(order.getShippingDistrict())
                .shippingWard(order.getShippingWard())
                .shippingAddressDetail(order.getShippingAddressDetail())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(item.getProductNameSnapshot() == null
                        ? product.getName()
                        : item.getProductNameSnapshot())
                .productSlug(product.getSlug())
                .variantId(getInventoryVariantId(item))
                .variantName(item.getVariantNameSnapshot() == null
                        ? getVariantName(variant)
                        : item.getVariantNameSnapshot())
                .sku(item.getSkuSnapshot() == null
                        ? variant == null ? product.getSku() : variant.getSku()
                        : item.getSkuSnapshot())
                .size(variant == null ? null : variant.getSize())
                .color(variant == null ? null : variant.getColor())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .productImage(variant != null && variant.getImage() != null
                        ? variant.getImage().getImageUrl()
                        : productImageRepository.findFirstByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId())
                            .map(image -> image.getImageUrl()).orElse(null))
                .shopName(product.getShop() == null ? "PPee" : product.getShop().getName())
                .build();
    }

    private record LineSelection(
            Product product,
            ProductVariant variant,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
