package com.restaurant.core.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** sortOrder mặc định 0, active mặc định true. Tên không trùng (không phân biệt hoa thường — UNIQUE lower(name)). */
public record CategoryRequest(@NotBlank @Size(max = 100) String name, Integer sortOrder, Boolean active) {
}
