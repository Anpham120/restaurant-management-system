package com.restaurant.core.menu.dto;

public record CategoryResponse(long id, String name, int sortOrder, boolean active) {
}
