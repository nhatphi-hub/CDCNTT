package com.smart_ecommerce.backend.controller;
import com.smart_ecommerce.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/seller/notifications") @RequiredArgsConstructor
public class SellerActivityController {
    private final SellerActivityService service;
    private final UserService users;
    @GetMapping public List<Map<String,Object>> mine(Authentication auth) {
        return service.feed(users.getUserIdByEmail(auth.getName()));
    }
}
