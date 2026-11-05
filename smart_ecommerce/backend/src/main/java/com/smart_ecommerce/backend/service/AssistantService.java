package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.controller.AssistantController.ChatResponse;
import com.smart_ecommerce.backend.controller.AssistantController.ProductSuggestion;
import com.smart_ecommerce.backend.dto.order.OrderResponse;
import com.smart_ecommerce.backend.dto.recommendation.RecommendationResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounded intent-based assistant (works without an external AI key).
 * All facts are read from PPee's own database. No generated prices, order
 * statuses or inventory. This is NOT a remote LLM and is deliberately read-only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssistantService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final OrderService orderService;
    private final UserService userService;
    private final RecommendationEngine recommendations;

    // A spending limit can be expressed as "dưới 100k", "chỉ có 100 ngàn",
    // "ngân sách khoảng 100 nghìn", etc. Treat it as a HARD ceiling.
    private static final Pattern BUDGET_BEFORE = Pattern.compile(
            "\\b(?:duoi|toi da|khong qua|khong hon|nho hon|khong vuot qua|"
            + "ngan sach|chi co|chi con|con moi|toi co|minh co|tui co|co moi|"
            + "trong tam|tam gia|khoang|tam|"
            + "under|below|budget|at most|up to|less than)\\b"
            + "(?:\\s+[a-z]+){0,5}\\s+(\\d[\\d.,]*)\\s*"
            + "(trieu|tr|nghin|ngan|k|d|vnd|dong)?(?=\\s|$)");
    private static final Pattern BUDGET_AFTER = Pattern.compile(
            "\\b(\\d[\\d.,]*)\\s*(trieu|tr|nghin|ngan|k|d|vnd|dong)?"
            + "\\s*(?:tro xuong|do lai|hoac thap hon|la toi da)\\b");
    private static final Pattern QUANTITY_REQUEST = Pattern.compile(
            "\\b(?:top|lay|xem)\\s+(\\d{1,2})\\b");
    // Generated order codes have the form ORD-YYYYMMDD-XXXXXXXX. Accept the
    // frequent customer typo OD-... (Vietnamese ÔD becomes OD after normalize)
    // and an ID without its ORD prefix. All lookups remain restricted to JWT's
    // authenticated customer's own orders.
    private static final Pattern ORDER_REFERENCE = Pattern.compile(
            "\\b(?:ord|od)-[a-z0-9]+(?:-[a-z0-9]+)+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_REFERENCE_WITHOUT_PREFIX = Pattern.compile(
            "\\b20\\d{6}-[a-z0-9]{8}\\b", Pattern.CASE_INSENSITIVE);

    private enum PriceOrder { MOST_EXPENSIVE, CHEAPEST, RELEVANCE }
    private record CatalogScan(List<Product> items, boolean truncated) { }

    public ChatResponse reply(String question, Authentication auth) {
        return reply(question, auth, List.of());
    }

    public ChatResponse reply(String question, Authentication auth, List<String> recentUserMessages) {
        String resolvedQuestion = resolveOrderFollowUp(
                resolveProductFollowUp(question, recentUserMessages), recentUserMessages);
        String q = normalize(resolvedQuestion);
        if (q.isBlank()) return simple("Bạn muốn PPee hỗ trợ điều gì?");
        // Order/delivery questions must never fall through to catalog search.
        // E.g. "đơn hàng tôi chưa được giao thành công tại sao vậy" is an
        // order-status question even though it does not contain "đơn hàng của tôi".
        if (isOrderInquiry(q) && !isHowToBuyQuestion(q)) {
            return ordersForCustomer(auth, q);
        }
        if (containsAny(q, "dat hang", "thanh toan", "checkout", "mua hang the nao")) {
            return simple("Bạn mở trang sản phẩm, chọn mẫu/size và số lượng, bấm Thêm vào giỏ hàng, "
                    + "vào giỏ chọn sản phẩm cần mua rồi Tiến hành thanh toán. "
                    + "Phí giao hàng và ưu đãi sẽ được xác nhận tại bước thanh toán. "
                    + "PPee hiện hỗ trợ COD và các hình thức thanh toán có trên màn thanh toán; "
                    + "trạng thái thanh toán phải được xác nhận bởi hệ thống, không phải bởi chatbot.");
        }
        if (containsAny(q, "gio hang", "cart", "yeu thich", "favorite", "tai khoan", "account")) {
            return simple("Giỏ hàng và Yêu thích ở thanh trên cùng. Bạn có thể chọn từng mặt hàng "
                    + "trong giỏ, đổi số lượng hoặc xóa trước khi thanh toán. "
                    + "Đăng nhập để lưu yêu thích, địa chỉ nhận hàng, đơn mua và xem lịch sử hoạt động.");
        }
        if (containsAny(q, "doi tra", "tra hang", "hoan tien", "return", "refund")) {
            return simple("Bạn mở Tài khoản → Đơn mua → Chi tiết đơn hàng để xem trạng thái và "
                    + "gửi yêu cầu trả hàng khi đơn đủ điều kiện. Việc tiếp nhận yêu cầu "
                    + "không đồng nghĩa tiền đã được hoàn; hãy kiểm tra trạng thái trong đơn.");
        }
        // An explicit product request takes precedence over personalized history.
        // A short question like "sơ mi nam" is also a catalog request.
        if (hasPhrase(q, "khong phai sach")) {
            return simple("Xin lỗi vì gợi ý không đúng nhu cầu. Bạn muốn tìm loại sản phẩm nào "
                    + "thay vì sách? Ví dụ: áo sơ mi nam hoặc áo thun.");
        }
        ProductIntent productIntent = identifyProduct(q);
        if (productIntent.kind() != null) {
            return searchCatalog(resolvedQuestion, productIntent);
        }
        if (containsAny(q, "goi y cho toi", "goi y theo so thich", "recommend for me")) {
            if (!isCustomer(auth)) return simple("Bạn đăng nhập tài khoản mua hàng để nhận gợi ý "
                    + "dựa trên lượt xem, yêu thích, tìm kiếm và mua hàng thực tế nhé.");
            UUID customerId = userService.getUserIdByEmail(auth.getName());
            List<RecommendationResponse> picks = recommendations.personalized(customerId, 5);
            if (picks.isEmpty()) return simple("Hiện chưa có đủ sản phẩm đang bán để đề xuất. "
                    + "Bạn có thể xem danh mục hoặc tìm kiếm sản phẩm mới.");
            List<ProductSuggestion> items = picks.stream().map(p -> new ProductSuggestion(
                    p.productId(), p.productName(), p.salePrice() == null ? p.price() : p.salePrice(),
                    p.imageUrl(), null)).toList();
            return new ChatResponse("Đây là những sản phẩm PPee gợi ý dựa trên hoạt động thực tế "
                    + "của tài khoản và danh mục đang bán:", items);
        }
        if (isProductInquiry(q) || priceOrder(q) != PriceOrder.RELEVANCE) {
            if (isGiftQuestion(q) && productIntent.kind() == null) {
                return simple("Bạn muốn tặng món nào (áo sơ mi, áo thun, sách...) và ngân sách "
                        + "khoảng bao nhiêu? Mình sẽ tìm sản phẩm đúng nhu cầu đang bán trên PPee.");
            }
            return searchCatalog(resolvedQuestion, productIntent);
        }
        if (isGiftQuestion(q) && productIntent.kind() == null) {
            return simple("Bạn muốn tặng món nào (áo sơ mi, áo thun, sách...) và ngân sách "
                    + "khoảng bao nhiêu? Mình sẽ tìm sản phẩm đúng nhu cầu đang bán trên PPee.");
        }
        // Do not force unrelated questions into a product search just because
        // some common words happen to match real product names.
        return simple("Mình có thể giúp bạn tìm sản phẩm, hướng dẫn mua hàng hoặc kiểm tra "
                + "trạng thái đơn của bạn. Bạn muốn hỗ trợ nội dung nào?");
    }

    private ChatResponse ordersForCustomer(Authentication auth, String normalizedQuestion) {
        if (!isCustomer(auth)) return simple(
                "Bạn cần đăng nhập bằng tài khoản mua hàng để xem đơn của chính mình. "
                + "PPee không cung cấp đơn hàng của người khác qua chat.");

        // Never look up an order using a user ID/order owner supplied in chat.
        UUID customerId = userService.getUserIdByEmail(auth.getName());
        List<OrderResponse> mine = orderService.getMyOrders(customerId);
        if (mine.isEmpty()) return simple("Tài khoản này chưa có đơn hàng nào.");

        String reference = extractOrderReference(normalizedQuestion);
        List<OrderResponse> matched = mine.stream()
                .filter(o -> o.getOrderCode() != null && (reference == null ||
                        canonicalOrderReference(o.getOrderCode()).equals(
                                canonicalOrderReference(reference))))
                .toList();
        if (reference != null && matched.isEmpty()) {
            return simple("Không tìm thấy mã đơn này trong tài khoản đang đăng nhập. "
                    + "Bạn hãy kiểm tra lại mã trong mục Đơn mua. "
                    + "PPee không cung cấp dữ liệu đơn hàng của tài khoản khác.");
        }

        boolean deliveryIssue = isDeliveryIssue(normalizedQuestion);
        if (reference != null) {
            // A request for one order must never return three unrelated orders.
            OrderResponse order = matched.getFirst();
            String status = orderStatusLabel(order.getStatus());
            String answer = "Mình đã kiểm tra đơn " + order.getOrderCode() + " của bạn:\n"
                    + "• Trạng thái: " + status + "\n"
                    + "• Thanh toán: " + paymentStatusLabel(order.getPaymentStatus()) + "\n"
                    + "• Tổng tiền: " + safeAmount(order.getTotalAmount()) + "\n";
            if (deliveryIssue) answer += deliveryAdvice(order.getStatus());
            else answer += "Bạn có thể mở Đơn mua → Chi tiết đơn hàng để xem thêm thông tin.";
            return simple(answer);
        }

        // For general delivery complaints show outstanding orders first; do not
        // imply a delivered order is still in transit.
        List<OrderResponse> inProgress = matched.stream()
                .filter(o -> !"DELIVERED".equalsIgnoreCase(o.getStatus())
                        && !"CANCELLED".equalsIgnoreCase(o.getStatus()))
                .toList();
        List<OrderResponse> candidates = deliveryIssue && !inProgress.isEmpty()
                ? inProgress : matched;
        List<OrderResponse> shown = candidates.stream().limit(3).toList();
        StringBuilder answer = new StringBuilder(deliveryIssue
                ? "Các đơn liên quan trong tài khoản PPee của bạn:\n"
                : "Các đơn hàng của bạn trên PPee:\n");
        for (OrderResponse order : shown) {
            answer.append("• ").append(order.getOrderCode()).append(": ")
                    .append(orderStatusLabel(order.getStatus()))
                    .append(" · ").append(safeAmount(order.getTotalAmount()))
                    .append(" · ").append(paymentStatusLabel(order.getPaymentStatus()))
                    .append("\n");
        }
        if (deliveryIssue) {
            answer.append("PPee chỉ có trạng thái ghi nhận trong hệ thống, chưa có "
                    + "nguyên nhân giao chậm từ hãng vận chuyển. ");
        }
        answer.append("Bạn gửi mã đơn cụ thể để mình kiểm tra chính xác một đơn, "
                + "hoặc mở Đơn mua → Chi tiết đơn hàng.");
        return simple(answer.toString());
    }

    // A brief follow-up such as "đơn đó giao chưa?" inherits only a recent
    // order reference, never an account ID or any private order data. The final
    // lookup is still restricted to the authenticated customer's orders.
    private static String resolveOrderFollowUp(String question, List<String> previous) {
        if (question == null || previous == null || previous.isEmpty()
                || question.length() > 500) return question;
        String q = normalize(question);
        if (extractOrderReference(q) != null || !hasAnyPhrase(q,
                "don nay", "don do", "don ay", "don vua roi", "don truoc"))
            return question;
        for (int i = previous.size() - 1; i >= Math.max(0, previous.size() - 4); i--) {
            String old = previous.get(i);
            if (old == null || old.length() > 500) continue;
            String reference = extractOrderReference(normalize(old));
            if (reference != null) return question + " " + reference;
        }
        return question;
    }

    private static String extractOrderReference(String question) {
        if (question == null) return null;
        Matcher prefixed = ORDER_REFERENCE.matcher(question);
        if (prefixed.find()) return prefixed.group();
        Matcher unprefixed = ORDER_REFERENCE_WITHOUT_PREFIX.matcher(question);
        return unprefixed.find() ? unprefixed.group() : null;
    }

    private static String canonicalOrderReference(String value) {
        String normalized = normalize(value).replaceAll("[^a-z0-9]", "");
        if (normalized.startsWith("ord")) return normalized.substring(3);
        if (normalized.startsWith("od")) return normalized.substring(2);
        return normalized;
    }

    private static String safeAmount(BigDecimal amount) {
        return amount == null ? "Chưa có thông tin" : formatVnd(amount);
    }

    private static String paymentStatusLabel(String status) {
        if (status == null) return "Chưa rõ thanh toán";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PAID" -> "Đã thanh toán";
            case "UNPAID" -> "Chưa thanh toán";
            case "PENDING" -> "Đang chờ thanh toán";
            case "FAILED" -> "Thanh toán thất bại";
            case "REFUNDED" -> "Đã hoàn tiền";
            default -> status;
        };
    }

    private static String deliveryAdvice(String status) {
        if ("DELIVERED".equalsIgnoreCase(status)) {
            return "PPee ghi nhận đã giao, nhưng điều đó không xác nhận bạn đã trực tiếp "
                    + "nhận hàng. Nếu chưa nhận được, vui lòng liên hệ hỗ trợ để đối soát.";
        }
        if ("CANCELLED".equalsIgnoreCase(status)) {
            return "Đơn đã hủy trên hệ thống; bạn xem chi tiết đơn hoặc liên hệ hỗ trợ "
                    + "nếu cần làm rõ lý do.";
        }
        return "PPee chưa có nguyên nhân giao chậm hay thời gian giao chính xác "
                + "từ hãng vận chuyển. Bạn hãy xem Đơn mua → Chi tiết đơn hàng "
                + "hoặc liên hệ hỗ trợ để kiểm tra.";
    }

    /** Categorize explicit product requests before consulting a user's history. */
    private record ProductIntent(String kind, String gender) { }

    private static ProductIntent identifyProduct(String q) {
        String gender = null;
        if (hasAnyPhrase(q, "ban trai", "con trai", "nam", "men", "boyfriend", "chong", "anh trai"))
            gender = "MALE";
        if (hasAnyPhrase(q, "ban gai", "con gai", "nu", "women", "woman", "girlfriend", "chi gai"))
            gender = "FEMALE";
        String kind = null;
        if (hasAnyPhrase(q, "so mi", "somi", "dress shirt", "button up shirt")) kind = "SO_MI";
        else if (hasAnyPhrase(q, "ao thun", "t shirt", "t-shirt", "tshirt", "tee shirt")) kind = "AO_THUN";
        else if (hasAnyPhrase(q, "polo")) kind = "POLO";
        else if (hasAnyPhrase(q, "hoodie", "ao ni")) kind = "HOODIE";
        else if (hasAnyPhrase(q, "ao khoac", "jacket", "bomber")) kind = "AO_KHOAC";
        else if (hasAnyPhrase(q, "quan jean", "jeans", "denim")) kind = "JEANS";
        else if (hasAnyPhrase(q, "quan short", "shorts")) kind = "SHORT";
        else if (hasAnyPhrase(q, "vay", "dam", "dress", "skirt")) kind = "VAY";
        else if (hasAnyPhrase(q, "sach", "book", "tieu thuyet", "truyen")) kind = "SACH";
        else if (hasAnyPhrase(q, "giay", "sneaker", "shoes", "sandal")) kind = "GIAY";
        else if (hasAnyPhrase(q, "dien thoai", "smartphone", "phone")) kind = "DIEN_THOAI";
        else if (hasAnyPhrase(q, "laptop", "may tinh xach tay")) kind = "LAPTOP";
        else if (hasAnyPhrase(q, "ca vat", "that lung", "phu kien", "tui xach")) kind = "PHU_KIEN";
        else if (hasAnyPhrase(q, "ao", "shirt")) kind = "AO";
        else if (hasAnyPhrase(q, "quan", "pants", "trousers")) kind = "QUAN";
        return new ProductIntent(kind, gender);
    }

    // Only reuse a recent customer question when the current turn is explicitly
    // correcting the assistant; never reuse client-provided history for order access.
    private static String resolveProductFollowUp(String current, List<String> previous) {
        if (current == null || previous == null || previous.isEmpty()) return current;
        String q = normalize(current);
        if (current.length() > 500 || isOrderInquiry(q) || isHowToBuyQuestion(q)
                || containsAny(q, "gio hang", "thanh toan", "tra hang", "hoan tien")) return current;
        boolean correction = hasAnyPhrase(q, "ao ma", "so mi ma", "y toi la ao",
                "khong phai sach", "toi dang hoi ao", "tui noi ao", "ao co ma");
        boolean shirtCorrection = hasAnyPhrase(q, "ao ma", "so mi ma", "y toi la ao",
                "toi dang hoi ao", "tui noi ao", "ao co ma");
        // A fresh budget or price-ranking question with no product name inherits
        // the last EXPLICITLY requested category, not the last recommended item.
        boolean constraintFollowUp = identifyProduct(q).kind() == null
                && (parseBudget(q) != null || priceOrder(q) != PriceOrder.RELEVANCE);
        if (!correction && !constraintFollowUp) return current;
        for (int i = previous.size() - 1; i >= Math.max(0, previous.size() - 4); i--) {
            String earlier = previous.get(i);
            if (earlier == null || earlier.length() > 500) continue;
            ProductIntent intent = identifyProduct(normalize(earlier));
            if (intent.kind() == null) continue;
            if (shirtCorrection && !List.of("SO_MI", "AO_THUN", "POLO", "HOODIE",
                    "AO_KHOAC", "AO").contains(intent.kind())) continue;
            if ("SACH".equals(intent.kind()) && hasPhrase(q, "khong phai sach")) continue;
            if (constraintFollowUp) {
                // Carry ONLY the category/gender; stale "đắt nhất" or an earlier
                // budget must not override the new customer's spending limit.
                return kindName(intent.kind(), intent.gender()) + " " + current;
            }
            return earlier;
        }
        return current;
    }

    private ChatResponse searchCatalog(String question, ProductIntent intent) {
        String normalized = normalize(question);
        BigDecimal maxPrice = parseBudget(normalized);
        PriceOrder order = priceOrder(normalized);
        List<String> terms = importantTokens(normalized);
        if (intent.kind() == null && terms.isEmpty() && order == PriceOrder.RELEVANCE)
            return simple("Bạn đang muốn tìm loại sản phẩm nào? Ví dụ: áo sơ mi nam, "
                    + "sách kỹ năng hoặc giày thể thao dưới 300k.");

        CatalogScan catalog = visibleCatalog();
        List<Product> filtered = catalog.items().stream()
                .filter(p -> p.getPrice() != null)
                .filter(p -> maxPrice == null || priceOf(p).compareTo(maxPrice) <= 0)
                .filter(p -> intent.kind() == null || matchesKind(p, intent.kind()))
                .filter(p -> matchesGender(p, intent.gender()))
                .filter(p -> intent.kind() != null || order != PriceOrder.RELEVANCE
                        || relevance(p, terms) > 0)
                .toList();

        Comparator<Product> byRelevance = Comparator.comparingInt((Product p) ->
                        relevance(p, terms) + genderBonus(p, intent.gender())).reversed()
                .thenComparing(Product::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Product::getId);
        Comparator<Product> sort = switch (order) {
            case MOST_EXPENSIVE -> Comparator.comparing(AssistantService::priceOf).reversed()
                    .thenComparing(byRelevance);
            case CHEAPEST -> Comparator.comparing(AssistantService::priceOf)
                    .thenComparing(byRelevance);
            case RELEVANCE -> byRelevance;
        };
        int count = (order == PriceOrder.RELEVANCE ? 5 : requestedCount(normalized));
        List<Product> matched = filtered.stream().sorted(sort).limit(count).toList();
        if (matched.isEmpty()) {
            String target = intent.kind() == null ? "sản phẩm" : kindName(intent.kind(), intent.gender());
            if (maxPrice != null) return simple("Hiện PPee chưa tìm thấy " + target
                    + " đang bán với giá không quá " + formatVnd(maxPrice)
                    + ". Mình sẽ không gợi ý mặt hàng vượt ngân sách. "
                    + "Bạn có muốn đổi loại sản phẩm hoặc điều chỉnh ngân sách không?");
            return simple("Hiện PPee chưa có " + target + " đang bán phù hợp với yêu cầu. "
                    + "Mình sẽ không đề xuất sản phẩm khác loại thay thế.");
        }
        List<ProductSuggestion> items = matched.stream().map(p -> new ProductSuggestion(
                p.getId(), p.getName(), priceOf(p),
                images.findFirstByProductIdOrderByIsPrimaryDescSortOrderAsc(p.getId())
                        .map(img -> img.getImageUrl()).orElse(null),
                p.getCategory() == null ? null : p.getCategory().getName())).toList();

        String target = intent.kind() == null ? "sản phẩm" : kindName(intent.kind(), intent.gender());
        String quantityTarget = "SACH".equals(intent.kind()) ? items.size() + " quyển sách"
                : items.size() + " " + target;
        String intro;
        if (order == PriceOrder.MOST_EXPENSIVE && items.size() == 1)
            intro = "Đây là " + target + " có giá cao nhất trong danh sách PPee đã kiểm tra: ";
        else if (order == PriceOrder.CHEAPEST && items.size() == 1)
            intro = "Đây là " + target + " có giá thấp nhất trong danh sách PPee đã kiểm tra: ";
        else if (order == PriceOrder.MOST_EXPENSIVE)
            intro = "Đây là " + quantityTarget + " được xếp từ giá cao xuống thấp";
        else if (order == PriceOrder.CHEAPEST)
            intro = "Đây là " + quantityTarget + " được xếp từ giá thấp lên cao";
        else
            intro = "Mình tìm thấy " + quantityTarget + " đang bán trên PPee";
        if (maxPrice != null) intro += " trong ngân sách tối đa " + formatVnd(maxPrice);
        if (isGiftQuestion(normalized)) intro += " để bạn cân nhắc làm quà tặng";
        if (catalog.truncated()) intro += ". Lưu ý: kết quả chỉ tính trên một phần catalog đang bán, không phải toàn bộ sản phẩm";
        return new ChatResponse(intro + ". Giá được lấy từ PPee; nhấn vào sản phẩm "
                + "để xem ảnh, phiên bản và thông tin chi tiết:", items);
    }

    private CatalogScan visibleCatalog() {
        // Read-only, stable paging prevents an arbitrary first-1000 cut from
        // silently producing the wrong "most expensive" product.
        final int pageSize = 250;
        final int maxPages = 40; // hard upper bound against unbounded chat scans
        List<Product> found = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
            Page<Product> page = products.findVisibleCatalog(PageRequest.of(
                    pageIndex, pageSize, Sort.by(Sort.Direction.ASC, "id")));
            found.addAll(page.getContent());
            if (!page.hasNext()) return new CatalogScan(found, false);
        }
        return new CatalogScan(found, true);
    }

    private static PriceOrder priceOrder(String q) {
        if (hasAnyPhrase(q, "dat tien nhat", "dat nhat", "mac nhat", "gia cao nhat",
                "gia dat nhat", "cao gia nhat", "most expensive", "highest price"))
            return PriceOrder.MOST_EXPENSIVE;
        if (hasAnyPhrase(q, "re nhat", "gia re nhat", "gia thap nhat",
                "gia tot nhat", "cheapest", "lowest price"))
            return PriceOrder.CHEAPEST;
        return PriceOrder.RELEVANCE;
    }

    private static int requestedCount(String q) {
        Matcher m = QUANTITY_REQUEST.matcher(q);
        if (m.find()) return Math.max(1, Math.min(10, Integer.parseInt(m.group(1))));
        return 1; // a singular superlative ("quyển sách đắt nhất")
    }

    private static String formatVnd(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(amount) + "đ";
    }

    private static String kindName(String kind, String gender) {
        String result = switch (kind) {
            case "SO_MI" -> "áo sơ mi";
            case "AO_THUN" -> "áo thun";
            case "POLO" -> "áo polo";
            case "HOODIE" -> "áo hoodie";
            case "AO_KHOAC" -> "áo khoác";
            case "JEANS" -> "quần jeans";
            case "SHORT" -> "quần short";
            case "VAY" -> "váy/đầm";
            case "SACH" -> "sách";
            case "GIAY" -> "giày";
            case "DIEN_THOAI" -> "điện thoại";
            case "LAPTOP" -> "laptop";
            case "PHU_KIEN" -> "phụ kiện";
            case "AO" -> "áo";
            default -> "quần";
        };
        return result + (gender == null ? "" : gender.equals("MALE") ? " nam" : " nữ");
    }

    private static boolean matchesKind(Product p, String kind) {
        String data = productText(p);
        return switch (kind) {
            // A generic "SHIRT" product type also covers tees. Require a
            // specific dress-shirt cue rather than leaking T-shirts into this query.
            case "SO_MI" -> hasAnyPhrase(data, "so mi", "somi", "dress shirt", "button up shirt");
            case "AO_THUN" -> hasAnyPhrase(data, "ao thun", "t shirt", "t-shirt", "tshirt", "tee shirt");
            case "POLO" -> hasPhrase(data, "polo");
            case "HOODIE" -> hasAnyPhrase(data, "hoodie", "ao ni");
            case "AO_KHOAC" -> hasAnyPhrase(data, "ao khoac", "jacket", "bomber");
            case "JEANS" -> hasAnyPhrase(data, "jean", "jeans", "denim");
            case "SHORT" -> hasAnyPhrase(data, "short", "shorts");
            case "VAY" -> hasAnyPhrase(data, "vay", "dam", "dress", "skirt");
            case "SACH" -> hasAnyPhrase(data, "sach", "book", "books", "truyen", "tieu thuyet");
            case "GIAY" -> hasAnyPhrase(data, "giay", "sneaker", "shoes", "sandal");
            case "DIEN_THOAI" -> hasAnyPhrase(data, "dien thoai", "phone", "smartphone");
            case "LAPTOP" -> hasAnyPhrase(data, "laptop", "may tinh xach tay");
            case "PHU_KIEN" -> hasAnyPhrase(data, "ca vat", "that lung", "phu kien", "tui xach");
            case "AO" -> hasAnyPhrase(data, "ao", "so mi", "polo", "shirt", "hoodie", "jacket", "bomber");
            case "QUAN" -> hasAnyPhrase(data, "quan", "jeans", "pants", "trousers", "shorts");
            default -> false;
        };
    }

    private static String productText(Product p) {
        String category = p.getCategory() == null ? "" : p.getCategory().getName();
        String parent = p.getCategory() == null || p.getCategory().getParent() == null
                ? "" : p.getCategory().getParent().getName();
        return normalize(p.getName() + " " + category + " " + parent + " " + p.getProductType());
    }

    private static boolean matchesGender(Product p, String desiredGender) {
        if (desiredGender == null) return true;
        String gender = normalize(p.getGender());
        String name = normalize(p.getName());
        boolean declaredFemale = hasAnyPhrase(gender, "female", "women", "woman", "nu");
        boolean declaredMale = hasAnyPhrase(gender, "male", "men", "man", "nam");
        boolean namedFemale = hasAnyPhrase(name, "nu", "women", "woman", "female", "girl");
        boolean namedMale = hasAnyPhrase(name, "nam", "men", "male", "boy");
        // Explicit variant metadata and product name take precedence over the
        // parent category (which may mix male/female subcategories).
        if ("MALE".equals(desiredGender)) {
            if (declaredFemale && !declaredMale) return false;
            if (namedFemale && !namedMale) return false;
        } else {
            if (declaredMale && !declaredFemale) return false;
            if (namedMale && !namedFemale) return false;
        }
        return true; // neutral/unisex goods remain eligible
    }

    private static int genderBonus(Product p, String gender) {
        if (gender == null) return 0;
        String explicit = normalize(p.getGender());
        String name = normalize(p.getName());
        if ("MALE".equals(gender) && hasAnyPhrase(explicit + " " + name, "nam", "male", "men")) return 20;
        if ("FEMALE".equals(gender) && hasAnyPhrase(explicit + " " + name, "nu", "female", "women")) return 20;
        return 0;
    }

    private static List<String> importantTokens(String q) {
        String withoutPrice = q.replaceAll("[0-9.,]+\\s*(trieu|tr|nghin|ngan|k|d|vnd)?", " ");
        String[] commonWords = {"toi", "muon", "mua", "tim", "goi", "y", "cho", "minh", "voi", "ban", "trai",
                "gai", "tang", "mot", "san", "pham", "gia", "duoi", "toi", "da", "khong", "qua", "co", "nao",
                "hay", "tu", "van", "theo", "so", "thich", "find", "me", "please", "product", "gift"};
        List<String> result = new ArrayList<>();
        for (String part : withoutPrice.split("[^a-z0-9]+")) {
            if (part.length() < 2) continue;
            boolean skip = false;
            for (String common : commonWords) if (part.equals(common)) { skip = true; break; }
            if (!skip) result.add(part);
        }
        return result;
    }

    private static int relevance(Product p, List<String> tokens) {
        String name = normalize(p.getName());
        String category = p.getCategory() == null ? "" : normalize(p.getCategory().getName());
        String parent = p.getCategory() == null || p.getCategory().getParent() == null
                ? "" : normalize(p.getCategory().getParent().getName());
        int score = 0;
        for (String term : tokens) {
            if (hasPhrase(name, term)) score += 6;
            if (hasPhrase(category, term)) score += 4;
            if (hasPhrase(parent, term)) score += 2;
        }
        return score;
    }

    private static boolean hasPhrase(String text, String phrase) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase) + "(?![a-z0-9])")
                .matcher(text).find();
    }

    private static boolean hasAnyPhrase(String text, String... phrases) {
        for (String phrase : phrases) if (hasPhrase(text, phrase)) return true;
        return false;
    }

    private static boolean isGiftQuestion(String q) {
        return hasAnyPhrase(q, "tang", "qua tang", "gift", "ban trai", "ban gai");
    }

    private static boolean isOrderInquiry(String q) {
        if (extractOrderReference(q) != null) return true;
        return containsAny(q,
                "don hang", "don mua", "don cua toi", "don da dat",
                "trang thai don", "kiem tra don", "don gan day", "theo doi don",
                "ma don", "ord-", "giao cham", "chua giao", "chua duoc giao",
                "khong duoc giao", "giao that bai", "giao thanh cong",
                "chua nhan hang", "chua nhan duoc hang", "khong nhan duoc hang",
                "van chuyen don", "tinh trang don", "my order", "order status", "track order",
                "delivery status", "where is my order");
    }

    private static boolean isHowToBuyQuestion(String q) {
        return containsAny(q, "cach dat hang", "huong dan dat hang",
                "lam sao de dat hang", "dat hang nhu the nao", "how to order");
    }

    private static boolean isDeliveryIssue(String q) {
        return containsAny(q, "giao cham", "giao that bai", "chua giao",
                "chua duoc giao", "giao thanh cong", "chua nhan hang",
                "chua nhan duoc hang", "khong nhan duoc hang")
                || (containsAny(q, "giao", "nhan", "van chuyen", "delivery")
                    && containsAny(q, "chua", "khong", "tre", "cham", "that bai"));
    }

    private static String orderStatusLabel(String status) {
        if (status == null) return "Chưa rõ trạng thái";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "SHIPPING" -> "Đang giao";
            case "DELIVERED" -> "Đã giao (theo hệ thống)";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    private static boolean isProductInquiry(String q) {
        // Word boundaries are important here: "ao" must not match "sao",
        // and "gia" must not match "giao" in shipping complaints.
        return Pattern.compile("\\b(?:tim|san pham|gia|bao nhieu|mua|sach|quan|ao|giay|"
                + "thoi trang|phu kien|dien thoai|laptop|tui|that lung|ca vat|"
                + "danh muc|tu van|book|product|find|shirt)\\b")
                .matcher(q).find();
    }

    static BigDecimal parseBudget(String question) {
        String q = normalize(question);
        Matcher before = BUDGET_BEFORE.matcher(q);
        Matcher after = BUDGET_AFTER.matcher(q);
        Matcher match = before.find() ? before : after.find() ? after : null;
        if (match == null) return null;
        String amount = match.group(1);
        String unit = match.group(2);
        try {
            boolean scaled = unit != null && List.of("k", "nghin", "ngan", "trieu", "tr").contains(unit);
            // "1,5 triệu" = 1.5m, but "100.000đ" = 100000đ and
            // "1.000k" = 1000000đ. Do not strip a decimal fraction blindly.
            boolean decimalWithUnit = scaled && amount.matches("\\d+[.,]\\d{1,2}");
            BigDecimal value = new BigDecimal(decimalWithUnit
                    ? amount.replace(',', '.') : amount.replace(".", "").replace(",", ""));
            if (unit != null && (unit.equals("k") || unit.equals("nghin") || unit.equals("ngan")))
                value = value.multiply(BigDecimal.valueOf(1000));
            if (unit != null && (unit.equals("trieu") || unit.equals("tr")))
                value = value.multiply(BigDecimal.valueOf(1_000_000));
            return value.setScale(0, RoundingMode.DOWN);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal priceOf(Product p) {
        return p.getSalePrice() != null && p.getSalePrice().signum() > 0
                && p.getSalePrice().compareTo(p.getPrice()) < 0
                ? p.getSalePrice() : p.getPrice();
    }
    private static ChatResponse simple(String answer) { return new ChatResponse(answer, List.of()); }
    private static boolean isCustomer(Authentication a) {
        return a != null && a.isAuthenticated() && a.getAuthorities().stream()
                .anyMatch(role -> "ROLE_CUSTOMER".equals(role.getAuthority()));
    }
    private static boolean containsAny(String input, String... words) {
        for (String word : words) if (input.contains(word)) return true;
        return false;
    }
    private static String normalize(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd');
        return ascii.replaceAll("\\s+", " ").trim();
    }
}
