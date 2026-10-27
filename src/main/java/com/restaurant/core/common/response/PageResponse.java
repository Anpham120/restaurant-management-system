package com.restaurant.core.common.response;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {
}
