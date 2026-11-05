package com.smart_ecommerce.backend.service;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

/** Read-only activity feed derived from actual shop orders and reviews; no fake events. */
@Service @RequiredArgsConstructor
public class SellerActivityService {
    private final ShopRepository shops;
    private final ShopOrderRepository shopOrders;
    private final ReviewRepository reviews;
    private final ShopReturnRequestRepository returnRequests;
    @Transactional(readOnly=true)
    public List<Map<String,Object>> feed(UUID ownerId) {
        Shop shop=shops.findByOwnerId(ownerId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN,"Chưa có shop"));
        if (!"ACTIVE".equalsIgnoreCase(shop.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Shop chưa hoạt động");
        List<Map<String,Object>> items=new ArrayList<>();
        for(ShopOrder so:shopOrders.findByShop_Id(shop.getId())) {
            items.add(entry("NEW_ORDER", "Đơn bán #"+so.getOrder().getOrderCode(),
                "Trạng thái phần hàng: "+so.getStatus(), so.getCreatedAt(),so.getOrder().getId()));
            if("PAID".equalsIgnoreCase(so.getOrder().getPaymentStatus()))
                items.add(entry("ORDER_PAID", "Đơn đã thanh toán",
                    "Đơn #"+so.getOrder().getOrderCode()+" đã được ghi nhận thanh toán", 
                    so.getOrder().getUpdatedAt(),so.getOrder().getId()));
        }
        for(Review review:reviews.findReviewsOfShop(shop.getId()))
            items.add(entry("NEW_REVIEW", "Đánh giá mới: "+review.getProduct().getName(),
                review.getRating()+"/5 sao",review.getCreatedAt(),review.getId()));
        // Returns are scoped by the authenticated owner's ACTIVE shop, not by client shopId.
        for (ShopReturnRequest request : returnRequests.findByShopIdOrderByCreatedAtDesc(shop.getId())) {
            String code = request.getOrder().getOrderCode();
            items.add(entry("RETURN_REQUESTED", "Yêu cầu trả hàng #" + code,
                "Khách đã gửi yêu cầu: " + request.getReason(),
                request.getCreatedAt(), request.getId()));
            if ("ACKNOWLEDGED".equalsIgnoreCase(request.getStatus())) {
                items.add(entry("RETURN_ACKNOWLEDGED", "Đã tiếp nhận yêu cầu #" + code,
                    "Shop đã ghi nhận yêu cầu; chưa có hoàn tiền tự động.",
                    request.getUpdatedAt(), request.getId()));
            } else if ("DISPUTED".equalsIgnoreCase(request.getStatus())) {
                items.add(entry("RETURN_DISPUTED", "Yêu cầu cần xác minh #" + code,
                    "Shop đã đánh dấu cần kiểm tra thêm; chưa có hoàn tiền tự động.",
                    request.getUpdatedAt(), request.getId()));
            }
        }
        items.sort(Comparator.comparing(m -> (LocalDateTime)m.get("createdAt"),
            Comparator.nullsLast(Comparator.reverseOrder())));
        return items.stream().limit(50).toList();
    }
    private Map<String,Object> entry(String type,String title,String message,LocalDateTime time,UUID id) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("type",type);data.put("title",title);data.put("message",message);
        data.put("createdAt",time);data.put("resourceId",id);return data;
    }
}
