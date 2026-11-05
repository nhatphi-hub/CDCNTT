package com.smart_ecommerce.backend.dto.seller;
import com.smart_ecommerce.backend.entity.Shop;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShopResponse(UUID id, UUID ownerId, String name, String primaryCategory,
    String description, String contactName, String contactPhone, String contactEmail,
    String pickupAddress, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ShopResponse from(Shop shop) {
        return new ShopResponse(shop.getId(), shop.getOwner().getId(), shop.getName(),
            shop.getPrimaryCategory(), shop.getDescription(), shop.getContactName(),
            shop.getContactPhone(), shop.getContactEmail(), shop.getPickupAddress(),
            shop.getStatus(), shop.getCreatedAt(), shop.getUpdatedAt());
    }
}
