package com.restaurant.core.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cách đã xử lý: "Đã hoàn 20.000 đ cho khách", "Khớp sao kê, thu bù tiền mặt"… */
public record ResolveIncidentRequest(@NotBlank @Size(max = 255) String note) {
}
