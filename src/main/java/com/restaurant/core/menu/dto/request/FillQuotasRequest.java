package com.restaurant.core.menu.dto.request;

import java.time.LocalDate;

/** fromDate null = thực đơn gần nhất trước ngày của thực đơn đang điền. */
public record FillQuotasRequest(LocalDate fromDate) {
}
