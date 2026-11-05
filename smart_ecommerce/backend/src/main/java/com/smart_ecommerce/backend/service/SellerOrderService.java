package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class SellerOrderService {
    private final ShopRepository shops;
    private final ShopOrderRepository shopOrders;
    private final SellerOrderItemRepository orderItems;
    private final OrderRepository orders;

    private Shop activeShop(UUID userId) {
        Shop shop = shops.findByOwnerId(userId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa có shop"));
        if (!"ACTIVE".equalsIgnoreCase(shop.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop chưa hoạt động");
        return shop;
    }

    @Transactional(readOnly = true)
    public Page<SellerOrderView> list(UUID userId, String status, int page, int size) {
        UUID shopId = activeShop(userId).getId();
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page/size không hợp lệ");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ShopOrder> result = status == null || status.isBlank()
            ? shopOrders.findByShop_Id(shopId, pageable)
            : shopOrders.findByShop_IdAndStatus(shopId, normalized(status), pageable);
        return result.map(so -> view(so, shopId));
    }

    @Transactional(readOnly = true)
    public SellerOrderView get(UUID userId, UUID orderId) {
        UUID shopId = activeShop(userId).getId();
        ShopOrder so = shopOrders.findByOrder_IdAndShop_Id(orderId, shopId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn của gian hàng"));
        return view(so, shopId);
    }

    @Transactional
    public SellerOrderView transition(UUID userId, UUID orderId, String requested) {
        UUID shopId = activeShop(userId).getId();
        // Global order lock serializes seller, buyer cancellation, STAFF status and shipping mutations.
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn của gian hàng"));
        ShopOrder so = shopOrders.lockOwned(orderId, shopId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn của gian hàng"));
        String next = normalized(requested);
        if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "DELIVERED".equalsIgnoreCase(order.getStatus())
                || "SHIPPING".equalsIgnoreCase(order.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn đã kết thúc hoặc đang giao");
        boolean allowed = ("PENDING".equals(so.getStatus()) && "CONFIRMED".equals(next))
            || ("CONFIRMED".equals(so.getStatus()) && "READY".equals(next));
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Chuyển trạng thái không hợp lệ: " + so.getStatus() + " → " + next);
        so.setStatus(next);
        shopOrders.saveAndFlush(so);
        // Do not confirm the entire order while some lines belong to unassigned legacy/catalog products.
        boolean allAssigned = order.getItems().stream().allMatch(i -> i.getShop() != null);
        if ("PENDING".equals(order.getStatus()) && allAssigned
                && shopOrders.findByOrder_Id(orderId).stream().allMatch(s ->
                    !"PENDING".equals(s.getStatus()))) {
            order.setStatus("CONFIRMED");
        }
        return view(so, shopId);
    }

    @Transactional(readOnly = true)
    public SellerOrderStats stats(UUID userId) {
        UUID shopId = activeShop(userId).getId();
        List<ShopOrder> list = shopOrders.findByShop_Id(shopId);
        Map<String, Long> counts = list.stream().collect(Collectors.groupingBy(
            ShopOrder::getStatus, TreeMap::new, Collectors.counting()));
        long units = 0;
        BigDecimal revenue = BigDecimal.ZERO.setScale(2);
        for (ShopOrder so : list) {
            List<OrderItem> items = orderItems.findByOrder_IdAndShop_Id(so.getOrder().getId(), shopId);
            Order o = so.getOrder();
            if ("DELIVERED".equalsIgnoreCase(o.getStatus())
                    && "PAID".equalsIgnoreCase(o.getPaymentStatus())
                    && !"CANCELLED".equals(so.getStatus())) {
                units += items.stream().mapToLong(i -> i.getQuantity()).sum();
                revenue = revenue.add(netAmount(o, items));
            }
        }
        return new SellerOrderStats(list.size(), units, counts, revenue);
    }

    /**
     * Reporting window is based on order creation date because orders have no
     * delivered_at timestamp. No shipping fee or unpaid / cancelled orders
     * are included in recognized revenue. Historical items without shop snapshots
     * are intentionally excluded to avoid accidental ownership assignment.
     */
    @Transactional(readOnly = true)
    public SellerAnalytics analytics(UUID userId, int days) {
        UUID shopId = activeShop(userId).getId();
        if (days != 7 && days != 30 && days != 90)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days phải là 7, 30 hoặc 90");
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        Map<LocalDate, BigDecimal> byDate = new TreeMap<>();
        Map<LocalDate, Long> completedByDate = new TreeMap<>();
        Map<String, Long> statusCounts = new TreeMap<>();
        Map<UUID, ProductTotals> products = new HashMap<>();
        long ordersCount = 0, completed = 0, units = 0;
        BigDecimal revenue = BigDecimal.ZERO.setScale(2);
        for (ShopOrder so : shopOrders.findByShop_Id(shopId)) {
            Order o = so.getOrder();
            if (o.getCreatedAt() == null) continue;
            LocalDate date = o.getCreatedAt().toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) continue;
            // Ignore stale shop_orders with no real lines for this shop.
            List<OrderItem> ownItems = orderItems.findByOrder_IdAndShop_Id(o.getId(), shopId);
            if (ownItems.isEmpty()) continue;
            ordersCount++;
            statusCounts.merge(so.getStatus(), 1L, Long::sum);
            if (!"DELIVERED".equalsIgnoreCase(o.getStatus())
                    || !"PAID".equalsIgnoreCase(o.getPaymentStatus())
                    || "CANCELLED".equalsIgnoreCase(so.getStatus())) continue;
            completed++;
            BigDecimal orderRevenue = netAmount(o, ownItems);
            revenue = revenue.add(orderRevenue);
            byDate.merge(date, orderRevenue, BigDecimal::add);
            completedByDate.merge(date, 1L, Long::sum);
            for (OrderItem item : ownItems) {
                units += item.getQuantity();
                UUID productId = item.getProduct().getId();
                ProductTotals total = products.computeIfAbsent(productId,
                    ignored -> new ProductTotals(productId, item.getProductNameSnapshot()));
                total.units += item.getQuantity();
                total.revenue = total.revenue.add(netAmount(o, List.of(item)));
            }
        }
        List<SellerAnalytics.DailyRevenue> daily = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            daily.add(new SellerAnalytics.DailyRevenue(day,
                byDate.getOrDefault(day, BigDecimal.ZERO.setScale(2)),
                completedByDate.getOrDefault(day, 0L)));
        }
        List<SellerAnalytics.TopProduct> top = products.values().stream()
            .sorted(Comparator.comparingLong((ProductTotals p) -> p.units).reversed()
                .thenComparing(p -> p.productId.toString()))
            .limit(10)
            .map(p -> new SellerAnalytics.TopProduct(p.productId, p.name, p.units, p.revenue))
            .toList();
        return new SellerAnalytics(days, from, to, ordersCount, completed, units,
            revenue, statusCounts, daily, top);
    }

    private static class ProductTotals {
        final UUID productId;
        final String name;
        long units = 0;
        BigDecimal revenue = BigDecimal.ZERO.setScale(2);
        ProductTotals(UUID productId, String name) { this.productId = productId; this.name = name; }
    }

    private SellerOrderView view(ShopOrder so, UUID shopId) {
        Order order = so.getOrder();
        List<OrderItem> items = orderItems.findByOrder_IdAndShop_Id(order.getId(), shopId);
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm của shop");
        BigDecimal sum = subtotal(items);
        BigDecimal net = netAmount(order, items);
        return new SellerOrderView(order.getId(), order.getOrderCode(), so.getStatus(),
            order.getStatus(), order.getPaymentStatus(), order.getPaymentMethod(),
            order.getShippingRecipientName(), order.getShippingPhone(),
            String.join(", ", order.getShippingAddressDetail(), order.getShippingWard(),
                order.getShippingDistrict(), order.getShippingProvince()),
            sum, sum.subtract(net), net, order.getCreatedAt(), so.getUpdatedAt(),
            items.stream().map(i -> new SellerOrderItemView(i.getId(), i.getProduct().getId(),
                i.getProductNameSnapshot(), i.getSkuSnapshot(), i.getVariantNameSnapshot(),
                i.getQuantity(), i.getUnitPrice(), i.getSubtotal())).toList());
    }

    private BigDecimal subtotal(List<OrderItem> items) {
        return items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Per-shop coupon allocation; shipping belongs to the order, not to either shop.
    // Round via cumulative order-item shares keyed by stable UUID to avoid double counting.
    private BigDecimal netAmount(Order order, List<OrderItem> items) {
        BigDecimal shopGross = subtotal(items);
        BigDecimal gross = order.getSubtotal();
        BigDecimal discount = order.getDiscountAmount();
        if (gross == null || gross.signum() <= 0 || discount == null || discount.signum() <= 0)
            return shopGross;
        // Shop coupons discount only the issuing shop, never other shops' revenue.
        if (order.getCoupon() != null && order.getCoupon().getShop() != null) {
            UUID issuerShop = order.getCoupon().getShop().getId();
            List<OrderItem> eligible = order.getItems().stream()
                .filter(i -> i.getShop() != null && issuerShop.equals(i.getShop().getId()))
                .sorted(Comparator.comparing(i -> i.getId().toString())).toList();
            BigDecimal eligibleGross = subtotal(eligible);
            if (eligibleGross.signum() <= 0) return shopGross;
            BigDecimal allocated = BigDecimal.ZERO;
            BigDecimal applicable = BigDecimal.ZERO;
            for (int x = 0; x < eligible.size(); x++) {
                OrderItem item = eligible.get(x);
                BigDecimal part = x == eligible.size() - 1 ? discount.subtract(allocated)
                    : discount.multiply(item.getSubtotal()).divide(eligibleGross, 2, RoundingMode.DOWN);
                allocated = allocated.add(part);
                if (items.stream().anyMatch(i -> i.getId().equals(item.getId()))) applicable = applicable.add(part);
            }
            return shopGross.subtract(applicable).max(BigDecimal.ZERO).setScale(2);
        }
        // Floor each item's allocation; the final item receives the remainder.
        List<OrderItem> all = new ArrayList<>(order.getItems());
        all.sort(Comparator.comparing(i -> i.getId().toString()));
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal shopDiscount = BigDecimal.ZERO;
        for (int x = 0; x < all.size(); x++) {
            OrderItem item = all.get(x);
            BigDecimal part = x == all.size()-1 ? discount.subtract(allocated)
                : discount.multiply(item.getSubtotal()).divide(gross, 2, RoundingMode.DOWN);
            allocated = allocated.add(part);
            if (items.stream().anyMatch(i -> i.getId().equals(item.getId()))) shopDiscount = shopDiscount.add(part);
        }
        return shopGross.subtract(shopDiscount).max(BigDecimal.ZERO).setScale(2);
    }

    private String normalized(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }
}
