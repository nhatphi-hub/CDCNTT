package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.coupon.CouponApplication;
import com.smart_ecommerce.backend.dto.coupon.CouponRequest;
import com.smart_ecommerce.backend.dto.coupon.CouponResponse;
import com.smart_ecommerce.backend.dto.coupon.CouponValidateRequest;
import com.smart_ecommerce.backend.dto.coupon.CouponValidateResponse;
import com.smart_ecommerce.backend.entity.Coupon;
import com.smart_ecommerce.backend.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponResponse create(CouponRequest request) {
        String code = normalize(request.code());

        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã coupon đã tồn tại");
        }

        validateDefinition(request);

        Coupon coupon = Coupon.builder()
                .code(code)
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .minOrderAmount(request.minOrderAmount())
                .maxDiscountAmount(request.maxDiscountAmount())
                .usageLimit(request.usageLimit())
                .usedCount(0)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .active(request.active() == null || request.active())
                .build();

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> getAll() {
        return couponRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponResponse getById(UUID id) {
        return toResponse(getCoupon(id));
    }

    public CouponResponse update(UUID id, CouponRequest request) {
        Coupon coupon = getCoupon(id);
        String code = normalize(request.code());

        couponRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã coupon đã tồn tại");
            }
        });

        validateDefinition(request);

        coupon.setCode(code);
        coupon.setDescription(request.description());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        coupon.setActive(request.active() == null || request.active());

        return toResponse(couponRepository.save(coupon));
    }

    public void delete(UUID id) {
        Coupon coupon = getCoupon(id);
        couponRepository.delete(coupon);
    }

    @Transactional(readOnly = true)
    public CouponValidateResponse validate(CouponValidateRequest request) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(normalize(request.code()))
                .orElse(null);

        if (coupon == null) {
            return invalid(request.code(), "Mã coupon không tồn tại");
        }

        if (coupon.getShop() != null) {
            return invalid(coupon.getCode(), "Mã theo shop chỉ được xác nhận khi đặt hàng");
        }
        String invalidReason = getInvalidReason(coupon, request.orderAmount());
        if (invalidReason != null) {
            return invalid(coupon.getCode(), invalidReason);
        }

        BigDecimal discount = calculateDiscount(coupon, request.orderAmount());
        BigDecimal finalAmount = request.orderAmount()
                .subtract(discount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new CouponValidateResponse(
                true,
                coupon.getCode(),
                "Coupon hợp lệ",
                discount,
                finalAmount
        );
    }

    /**
     * Checks and consumes one global use while holding the coupon row lock.
     * The caller's order transaction includes this counter update, so any
     * later order failure rolls the coupon use back as well.
     */
    public CouponApplication applyToOrder(String rawCode, BigDecimal subtotal,
                                          java.util.Map<UUID, BigDecimal> shopSubtotals) {
        String code = normalize(rawCode);
        Coupon coupon = couponRepository.findByCodeIgnoreCaseForUpdate(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Mã coupon không tồn tại"));

        BigDecimal eligible = subtotal;
        if (coupon.getShop() != null) {
            if (!"ACTIVE".equalsIgnoreCase(coupon.getShop().getStatus()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gian hàng phát hành mã không hoạt động");
            eligible = shopSubtotals.getOrDefault(coupon.getShop().getId(), BigDecimal.ZERO);
            if (eligible.signum() <= 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giỏ hàng không có sản phẩm thuộc shop phát hành mã");
        }
        String invalidReason = getInvalidReason(coupon, eligible);
        if (invalidReason != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidReason);
        }

        BigDecimal discount = calculateDiscount(coupon, eligible);
        if (couponRepository.incrementUsedCount(coupon.getId()) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể cập nhật lượt sử dụng coupon");
        }

        return new CouponApplication(coupon.getId(), coupon.getCode(), discount);
    }

    private String getInvalidReason(Coupon coupon, BigDecimal orderAmount) {
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return "Coupon đang bị vô hiệu hóa";
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            return "Coupon chưa bắt đầu hoặc đã hết hạn";
        }

        int usedCount = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
        if (coupon.getUsageLimit() != null && usedCount >= coupon.getUsageLimit()) {
            return "Coupon đã hết lượt sử dụng";
        }

        if (orderAmount == null || orderAmount.signum() < 0) {
            return "Giá trị đơn hàng không hợp lệ";
        }

        if (coupon.getMinOrderAmount() != null
                && orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            return "Đơn hàng chưa đạt giá trị tối thiểu " + coupon.getMinOrderAmount();
        }

        return null;
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal discount;
        if (coupon.getDiscountType() == Coupon.DiscountType.PERCENT) {
            discount = orderAmount
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue();
        }

        if (coupon.getMaxDiscountAmount() != null
                && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }

        return discount.max(BigDecimal.ZERO)
                .min(orderAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateDefinition(CouponRequest request) {
        if (request.endAt().isBefore(request.startAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        if (request.discountType() == Coupon.DiscountType.PERCENT
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Giảm theo phần trăm không được vượt quá 100%");
        }

        if (request.maxDiscountAmount() != null
                && request.maxDiscountAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giảm tối đa không hợp lệ");
        }
    }

    private Coupon getCoupon(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy coupon"));
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CouponValidateResponse invalid(String code, String message) {
        return new CouponValidateResponse(
                false, code, message, BigDecimal.ZERO, null
        );
    }

    private CouponResponse toResponse(Coupon c) {
        return new CouponResponse(
                c.getId(),
                c.getCode(),
                c.getDescription(),
                c.getDiscountType(),
                c.getDiscountValue(),
                c.getMinOrderAmount(),
                c.getMaxDiscountAmount(),
                c.getUsageLimit(),
                c.getUsedCount(),
                c.getStartAt(),
                c.getEndAt(),
                c.getActive()
        );
    }
}
