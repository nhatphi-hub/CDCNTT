package com.smart_ecommerce.backend.service;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** No automatic refund: money movement requires an independent, auditable payment provider integration. */
@Service @RequiredArgsConstructor
public class ShopReturnService {
    private final ShopReturnRequestRepository requests;
    private final OrderRepository orders;
    private final ShopRepository shops;
    private final OrderItemRepository items;
    private final UserRepository users;

    private Shop activeShop(UUID ownerId) {
        Shop shop=shops.findByOwnerId(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"Chưa có shop"));
        if(!"ACTIVE".equalsIgnoreCase(shop.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Shop chưa hoạt động");
        return shop;
    }
    private Map<String,Object> dto(ShopReturnRequest r) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",r.getId());m.put("orderId",r.getOrder().getId());
        m.put("orderCode",r.getOrder().getOrderCode());
        m.put("shopName",r.getShop().getName());m.put("status",r.getStatus());
        m.put("reason",r.getReason());m.put("sellerNote",r.getSellerNote());
        m.put("createdAt",r.getCreatedAt());m.put("updatedAt",r.getUpdatedAt());
        return m;
    }
    @Transactional
    public Map<String,Object> create(UUID customerId,UUID orderId,UUID productId,String reason) {
        Order order=orders.findByIdAndUserId(orderId, customerId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng"));
        if(!"DELIVERED".equalsIgnoreCase(order.getStatus()) || !"PAID".equalsIgnoreCase(order.getPaymentStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ yêu cầu trả hàng khi đơn đã giao và thanh toán");
        OrderItem line=order.getItems().stream()
            .filter(i -> i.getProduct().getId().equals(productId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Sản phẩm không thuộc đơn hàng"));
        if(line.getShop()==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn cũ chưa có thông tin shop để xử lý riêng");
        String message=reason==null?"":reason.trim();
        if(message.length()<10 || message.length()>1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lý do phải từ 10 đến 1000 ký tự");
        if(requests.findByOrderIdAndShopId(orderId,line.getShop().getId()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã có yêu cầu trả hàng cho phần đơn này");
        User customer=users.getReferenceById(customerId);
        ShopReturnRequest req=ShopReturnRequest.builder().order(order).shop(line.getShop())
            .customer(customer).reason(message).status("REQUESTED").build();
        return dto(requests.saveAndFlush(req));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine(UUID customerId) {
        return requests.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::dto).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> seller(UUID ownerId) {
        return requests.findByShopIdOrderByCreatedAtDesc(activeShop(ownerId).getId()).stream().map(this::dto).toList();
    }
    @Transactional
    public Map<String,Object> acknowledge(UUID ownerId,UUID id,String status,String note) {
        Shop shop=activeShop(ownerId);
        ShopReturnRequest request=requests.lock(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu"));
        if(!shop.getId().equals(request.getShop().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu");
        if(!"REQUESTED".equals(request.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã được xử lý");
        if(!List.of("ACKNOWLEDGED","DISPUTED").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chỉ dùng ACKNOWLEDGED hoặc DISPUTED");
        request.setStatus(status);
        request.setSellerNote(note==null?null:note.trim().substring(0,Math.min(1000,note.trim().length())));
        return dto(requests.saveAndFlush(request));
    }
}
