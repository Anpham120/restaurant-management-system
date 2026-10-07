package vn.khoibep.rms.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.entity.Category;
import vn.khoibep.rms.entity.MenuItem;
import vn.khoibep.rms.enums.Channel;

public final class MenuDtos {

    private MenuDtos() {
    }

    public record CategoryDto(Long id, String name, int sortOrder) {
        public static CategoryDto from(Category c) {
            return new CategoryDto(c.getId(), c.getName(), c.getSortOrder());
        }
    }

    public record CategoryRequest(@NotBlank @Size(max = 100) String name,
                                  @NotNull @Min(0) @Max(1000) Integer sortOrder) {
    }

    public record MenuItemDto(Long id, Long categoryId, String categoryName, String name, long price,
                              String description, boolean available, Long taxCategoryId, String taxCategoryName,
                              Map<Channel, Long> appPrices) {
        public static MenuItemDto from(MenuItem m) {
            return new MenuItemDto(m.getId(), m.getCategory().getId(), m.getCategory().getName(), m.getName(),
                    m.getPrice(), m.getDescription(), m.isAvailable(), m.getTaxCategory().getId(),
                    m.getTaxCategory().getName(), Map.copyOf(m.getAppPrices()));
        }
    }

    public record MenuItemRequest(@NotNull Long categoryId,
                                  @NotBlank @Size(max = 150) String name,
                                  @NotNull @Min(0) @Max(100_000_000) Long price,
                                  @Size(max = 500) String description,
                                  Boolean available,
                                  Long taxCategoryId,
                                  Map<Channel, @Min(0) @Max(100_000_000) Long> appPrices) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }

    /** One category of the guest menu, available items only. */
    public record MenuSectionDto(Long categoryId, String categoryName, List<MenuItemDto> items) {
    }
}
