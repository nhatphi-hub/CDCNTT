package com.smart_ecommerce.backend.dto.searchhistory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SearchHistoryRequest {
    @NotBlank(message = "Keyword không được để trống")
    @Size(max = 255, message = "Keyword tối đa 255 ký tự")
    private String keyword;
}
