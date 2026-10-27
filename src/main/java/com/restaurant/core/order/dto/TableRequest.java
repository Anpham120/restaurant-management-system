package com.restaurant.core.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Tạo/sửa bàn. Không có status: AVAILABLE/OCCUPIED chỉ đổi qua mở/đóng phiên (hàm rc_*). */
public record TableRequest(@NotBlank @Size(max = 20) String code, @NotNull @Positive @Max(100) Integer capacity) {
}
