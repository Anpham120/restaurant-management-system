package com.restaurant.core.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do bắt buộc (hủy món, từ chối, bỏ về…). DB kiểm lại lý do không rỗng. */
public record ReasonRequest(@NotBlank @Size(max = 200) String reason) {
}
