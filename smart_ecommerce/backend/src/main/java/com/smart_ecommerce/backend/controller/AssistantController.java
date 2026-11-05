package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.service.AssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/** Read-only, grounded customer assistant. No payment or order writes. */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final AssistantService assistantService;

    // Backwards-compatible: recentUserMessages is optional (old clients send message only).
    // History is only a hint for correcting product queries, never for authorization.
    public record ChatRequest(@NotBlank @Size(max = 500) String message,
                              @Size(max = 4) List<@Size(max = 500) String> recentUserMessages) { }
    public record ProductSuggestion(UUID id, String name, BigDecimal price,
                                    String imageUrl, String categoryName) { }
    public record ChatResponse(String answer, List<ProductSuggestion> products) { }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request, Authentication authentication) {
        return ResponseEntity.ok(assistantService.reply(request.message(), authentication, request.recentUserMessages()));
    }
}
