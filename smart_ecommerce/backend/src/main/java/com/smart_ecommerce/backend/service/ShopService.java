package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class ShopService {
    private final ShopRepository shops;
    private final UserRepository users;
    private final RoleRepository roles;

    @Transactional
    public ShopResponse apply(UUID ownerId, ShopRequest request) {
        User owner = users.findById(ownerId).orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
        if (shops.existsByOwnerId(ownerId)) throw failure(HttpStatus.CONFLICT, "Tài khoản đã đăng ký một gian hàng");
        Shop shop = Shop.builder().owner(owner).status("PENDING").build();
        fill(shop, request);
        return ShopResponse.from(shops.saveAndFlush(shop));
    }

    @Transactional(readOnly = true)
    public ShopResponse mine(UUID ownerId) {
        return ShopResponse.from(owned(ownerId));
    }

    @Transactional
    public ShopResponse updateMine(UUID ownerId, ShopRequest request) {
        Shop shop = owned(ownerId);
        if ("SUSPENDED".equals(shop.getStatus())) throw failure(HttpStatus.FORBIDDEN, "Gian hàng bị đình chỉ");
        fill(shop, request);
        // Approved shop stays active; previously rejected shop must be reviewed again.
        if ("REJECTED".equals(shop.getStatus())) shop.setStatus("PENDING");
        return ShopResponse.from(shops.saveAndFlush(shop));
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> pending() {
        return shops.findByStatusOrderByCreatedAtAsc("PENDING").stream().map(ShopResponse::from).toList();
    }

    @Transactional
    public ShopResponse decide(UUID shopId, String newStatus) {
        Shop shop = shops.findById(shopId).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Không tìm thấy gian hàng"));
        if ("REJECTED".equals(newStatus) && "ACTIVE".equals(shop.getStatus()))
            throw failure(HttpStatus.BAD_REQUEST, "Dùng SUSPENDED để khóa gian hàng đang hoạt động");
        if ("ACTIVE".equals(newStatus)) {
            Role sellerRole = roles.findByNameIgnoreCase("SELLER").orElseThrow(() ->
                new IllegalStateException("Chưa tạo role SELLER; hãy chạy migration SQL"));
            User owner = shop.getOwner();
            owner.getRoles().add(sellerRole);
            users.save(owner);
        } else {
            User owner = shop.getOwner();
            owner.getRoles().removeIf(role -> "SELLER".equalsIgnoreCase(role.getName()));
            users.save(owner);
        }
        shop.setStatus(newStatus);
        return ShopResponse.from(shops.saveAndFlush(shop));
    }

    private Shop owned(UUID ownerId) {
        return shops.findByOwnerId(ownerId).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Tài khoản chưa có gian hàng"));
    }
    private void fill(Shop s, ShopRequest r) {
        s.setName(r.name().trim()); s.setPrimaryCategory(r.primaryCategory().trim());
        s.setDescription(r.description() == null ? null : r.description().trim());
        s.setContactName(r.contactName().trim()); s.setContactPhone(r.contactPhone().trim());
        s.setContactEmail(r.contactEmail().trim()); s.setPickupAddress(r.pickupAddress().trim());
    }
    private ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
