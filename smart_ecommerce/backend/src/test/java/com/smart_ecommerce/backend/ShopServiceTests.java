package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.seller.ShopRequest;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopServiceTests {
    private final ShopRepository shops = mock(ShopRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ShopService service = new ShopService(shops, users, roles);
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private final ShopRequest request = new ShopRequest("Shop A", "Công nghệ", "Description", "Nguyen A",
        "0912345678", "a@example.com", "123 Street");

    @Test void creatingShopAlwaysStartsPending() {
        User owner = User.builder().id(ownerA).roles(new HashSet<>()).build();
        when(users.findById(ownerA)).thenReturn(Optional.of(owner));
        when(shops.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> {
            Shop saved = invocation.getArgument(0);
            assertEquals("PENDING", saved.getStatus());
            assertEquals(ownerA, saved.getOwner().getId());
            saved.setId(UUID.randomUUID());
            return saved;
        });
        assertEquals("PENDING", service.apply(ownerA, request).status());
        verify(shops).saveAndFlush(any(Shop.class));
    }

    @Test void cannotRegisterSecondShop() {
        when(users.findById(ownerA)).thenReturn(Optional.of(User.builder().id(ownerA).build()));
        when(shops.existsByOwnerId(ownerA)).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.apply(ownerA, request));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(shops, never()).saveAndFlush(any());
    }

    @Test void otherShopCannotBeReadThroughMine() {
        Shop other = Shop.builder().owner(User.builder().id(ownerB).build()).name("Shop B").build();
        when(shops.findByOwnerId(ownerA)).thenReturn(Optional.empty());
        when(shops.findByOwnerId(ownerB)).thenReturn(Optional.of(other));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.mine(ownerA));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(shops, never()).findByOwnerId(ownerB);
    }

    @Test void adminApprovalAssignsSellerRoleWithoutAdminOrStaff() {
        User owner = User.builder().id(ownerA).roles(new HashSet<>()).build();
        Shop shop = Shop.builder().id(UUID.randomUUID()).owner(owner).status("PENDING").build();
        Role sellerRole = Role.builder().name("SELLER").build();
        when(shops.findById(shop.getId())).thenReturn(Optional.of(shop));
        when(roles.findByNameIgnoreCase("SELLER")).thenReturn(Optional.of(sellerRole));
        when(shops.saveAndFlush(shop)).thenReturn(shop);
        assertEquals("ACTIVE", service.decide(shop.getId(), "ACTIVE").status());
        assertTrue(owner.getRoles().stream().anyMatch(r -> "SELLER".equals(r.getName())));
        assertFalse(owner.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()) || "STAFF".equals(r.getName())));
    }
}
