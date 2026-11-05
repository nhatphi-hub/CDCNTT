package com.smart_ecommerce.backend.controller;
import com.smart_ecommerce.backend.service.ProductMediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/media/products") @RequiredArgsConstructor
public class ProductMediaController {
    private final ProductMediaStorage storage;
    @GetMapping("/{name}") public ResponseEntity<Resource> image(@PathVariable String name) {
        return storage.read(name);
    }
}
