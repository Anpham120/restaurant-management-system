package vn.khoibep.rms.menu.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.menu.dto.MenuDtos.CategoryDto;
import vn.khoibep.rms.menu.dto.MenuDtos.CategoryRequest;
import vn.khoibep.rms.menu.dto.MenuDtos.MenuItemDto;
import vn.khoibep.rms.menu.dto.MenuDtos.MenuItemRequest;
import vn.khoibep.rms.menu.dto.MenuDtos.MenuSectionDto;
import vn.khoibep.rms.menu.entity.Category;
import vn.khoibep.rms.menu.entity.MenuItem;
import vn.khoibep.rms.menu.repository.CategoryRepository;
import vn.khoibep.rms.menu.repository.MenuItemRepository;
import vn.khoibep.rms.order.repository.OrderItemRepository;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categories;
    private final MenuItemRepository menuItems;
    private final OrderItemRepository orderItems;
    private final RealtimeEvents realtime;

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest request) {
        if (categories.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("Danh mục đã tồn tại");
        }
        Category category = new Category();
        apply(category, request);
        CategoryDto dto = CategoryDto.from(categories.save(category));
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
        return dto;
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest request) {
        if (categories.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Danh mục đã tồn tại");
        }
        Category category = category(id);
        apply(category, request);
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
        return CategoryDto.from(category);
    }

    /** BR-18: a category that still has dishes cannot be deleted. */
    @Transactional
    public void deleteCategory(Long id) {
        if (menuItems.existsByCategoryId(id)) {
            throw ApiException.conflict("Danh mục còn món, không xoá được");
        }
        categories.delete(category(id));
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> items() {
        return menuItems.findAllWithCategory().stream().map(MenuItemDto::from).toList();
    }

    /** Guest menu: dishes on sale, grouped by category (FR-06.1). */
    @Transactional(readOnly = true)
    public List<MenuSectionDto> guestMenu() {
        Map<Long, MenuSectionDto> sections = new LinkedHashMap<>();
        for (MenuItem item : menuItems.findAllWithCategory()) {
            if (!item.isAvailable()) {
                continue;
            }
            Category c = item.getCategory();
            sections.computeIfAbsent(c.getId(), k -> new MenuSectionDto(c.getId(), c.getName(), new ArrayList<>()))
                    .items().add(MenuItemDto.from(item));
        }
        return List.copyOf(sections.values());
    }

    @Transactional
    public MenuItemDto createItem(MenuItemRequest request) {
        MenuItem item = new MenuItem();
        apply(item, request);
        MenuItemDto dto = MenuItemDto.from(menuItems.save(item));
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
        return dto;
    }

    /** BR-05: orders keep the name and price they were placed with, so editing here does not change them. */
    @Transactional
    public MenuItemDto updateItem(Long id, MenuItemRequest request) {
        MenuItem item = item(id);
        apply(item, request);
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
        return MenuItemDto.from(item);
    }

    /** BR-18: a dish that has been ordered cannot be deleted; mark it sold out instead. */
    @Transactional
    public void deleteItem(Long id) {
        if (orderItems.existsByMenuItemId(id)) {
            throw ApiException.conflict("Món đã có trong đơn, hãy đánh dấu hết món thay vì xoá");
        }
        menuItems.delete(item(id));
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
    }

    @Transactional
    public MenuItemDto setAvailability(Long id, boolean available) {
        MenuItem item = item(id);
        item.setAvailable(available);
        realtime.staffNotice(RealtimeEvent.MENU_CHANGED);
        return MenuItemDto.from(item);
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder());
    }

    private void apply(MenuItem item, MenuItemRequest request) {
        item.setCategory(category(request.categoryId()));
        item.setName(request.name().trim());
        item.setPrice(request.price());
        item.setDescription(request.description());
        if (request.available() != null) {
            item.setAvailable(request.available());
        }
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục"));
    }

    private MenuItem item(Long id) {
        return menuItems.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy món"));
    }
}
