package com.smart_ecommerce.backend.controller;
import com.smart_ecommerce.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ShopReturnController {
    private final ShopReturnService service;
    private final UserService users;
    private UUID user(Authentication a) {return users.getUserIdByEmail(a.getName());}
    @PostMapping("/api/orders/{orderId}/returns") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication a,@PathVariable UUID orderId,
           @RequestBody Map<String,String> req) {
        UUID productId;
        try {productId=UUID.fromString(req.get("productId"));}
        catch (Exception e) {throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"productId không hợp lệ");}
        return service.create(user(a),orderId,productId,req.get("reason"));
    }
    @GetMapping("/api/returns/mine")
    public List<Map<String,Object>> mine(Authentication a) {return service.mine(user(a));}
    @GetMapping("/api/seller/returns")
    public List<Map<String,Object>> shop(Authentication a) {return service.seller(user(a));}
    @PatchMapping("/api/seller/returns/{id}")
    public Map<String,Object> update(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String> body) {
        return service.acknowledge(user(a),id,body.get("status"),body.get("note"));
    }
}
