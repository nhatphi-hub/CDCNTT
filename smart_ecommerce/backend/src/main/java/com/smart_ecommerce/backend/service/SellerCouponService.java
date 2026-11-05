package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.coupon.CouponRequest;
import com.smart_ecommerce.backend.dto.coupon.CouponResponse;
import com.smart_ecommerce.backend.entity.Coupon;
import com.smart_ecommerce.backend.entity.Shop;
import com.smart_ecommerce.backend.repository.CouponRepository;
import com.smart_ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class SellerCouponService {
    private final CouponRepository coupons;
    private final ShopRepository shops;

    private Shop activeShop(UUID ownerId) {
        Shop shop = shops.findByOwnerId(ownerId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa có gian hàng"));
        if (!"ACTIVE".equalsIgnoreCase(shop.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop chưa hoạt động");
        return shop;
    }
    private Coupon owned(UUID ownerId, UUID id) {
        UUID shopId = activeShop(ownerId).getId();
        Coupon coupon = coupons.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã giảm giá"));
        if (coupon.getShop() == null || !shopId.equals(coupon.getShop().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã giảm giá");
        return coupon;
    }
    private void check(CouponRequest request) {
        if (request.endAt().isBefore(request.startAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        if (request.discountType() == Coupon.DiscountType.PERCENT
                && request.discountValue().compareTo(new BigDecimal("100")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giảm phần trăm không vượt 100%");
    }
    private String code(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private CouponResponse dto(Coupon c) {
        return new CouponResponse(c.getId(),c.getCode(),c.getDescription(),c.getDiscountType(),
            c.getDiscountValue(),c.getMinOrderAmount(),c.getMaxDiscountAmount(),c.getUsageLimit(),
            c.getUsedCount(),c.getStartAt(),c.getEndAt(),c.getActive());
    }
    @Transactional(readOnly=true)
    public List<CouponResponse> mine(UUID ownerId) {
        return coupons.findByShopIdOrderByStartAtDesc(activeShop(ownerId).getId()).stream().map(this::dto).toList();
    }
    @Transactional
    public CouponResponse create(UUID ownerId, CouponRequest request) {
        Shop shop = activeShop(ownerId); check(request);
        String code = code(request.code());
        if (coupons.existsByCodeIgnoreCase(code))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã đã tồn tại trong hệ thống");
        Coupon c = Coupon.builder().shop(shop).code(code).description(request.description())
            .discountType(request.discountType()).discountValue(request.discountValue())
            .minOrderAmount(request.minOrderAmount()).maxDiscountAmount(request.maxDiscountAmount())
            .usageLimit(request.usageLimit()).usedCount(0).startAt(request.startAt())
            .endAt(request.endAt()).active(request.active()==null || request.active()).build();
        return dto(coupons.saveAndFlush(c));
    }
    @Transactional
    public CouponResponse update(UUID ownerId, UUID id, CouponRequest request) {
        Coupon c = owned(ownerId,id);
        check(request);
        String code=code(request.code());
        if (!code.equalsIgnoreCase(c.getCode())) {
            if (c.getUsedCount() != null && c.getUsedCount()>0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không đổi mã đã được sử dụng; hãy tạo mã mới");
            if (coupons.existsByCodeIgnoreCase(code))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã đã tồn tại");
        }
        c.setCode(code); c.setDescription(request.description());
        c.setDiscountType(request.discountType()); c.setDiscountValue(request.discountValue());
        c.setMinOrderAmount(request.minOrderAmount());
        c.setMaxDiscountAmount(request.maxDiscountAmount());
        c.setUsageLimit(request.usageLimit());
        if (request.usageLimit() != null && c.getUsedCount() != null
                && request.usageLimit() < c.getUsedCount())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Giới hạn mới nhỏ hơn lượt đã dùng");
        c.setStartAt(request.startAt()); c.setEndAt(request.endAt());
        c.setActive(request.active() == null || request.active());
        return dto(coupons.saveAndFlush(c));
    }

    @Transactional
    public CouponResponse setActive(UUID ownerId, UUID id, boolean active) {
        Coupon c = owned(ownerId,id); c.setActive(active); return dto(coupons.saveAndFlush(c));
    }
}
