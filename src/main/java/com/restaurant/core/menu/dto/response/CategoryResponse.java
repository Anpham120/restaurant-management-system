package com.restaurant.core.menu.dto.response;

public record CategoryResponse(long id, String name, int sortOrder, boolean active) {
}
