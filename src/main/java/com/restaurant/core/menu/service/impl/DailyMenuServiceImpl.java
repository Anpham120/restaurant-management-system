package com.restaurant.core.menu.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.menu.QuotaUsageCounter;
import com.restaurant.core.menu.dto.request.DailyMenuItemRequest;
import com.restaurant.core.menu.dto.response.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.response.DailyMenuResponse;
import com.restaurant.core.menu.dto.response.FillQuotasResponse;
import com.restaurant.core.menu.dto.response.IngredientUsageRow;
import com.restaurant.core.menu.dto.request.QuotasRequest;
import com.restaurant.core.menu.entity.DailyMenu;
import com.restaurant.core.menu.entity.DailyMenuItem;
import com.restaurant.core.menu.entity.DailyMenuStatus;
import com.restaurant.core.menu.entity.MenuItem;
import com.restaurant.core.menu.entity.Recipe;
import com.restaurant.core.menu.repository.DailyMenuItemRepository;
import com.restaurant.core.menu.repository.DailyMenuQueryRepository;
import com.restaurant.core.menu.repository.DailyMenuRepository;
import com.restaurant.core.menu.repository.MenuItemRepository;
import com.restaurant.core.menu.repository.RecipeRepository;
import com.restaurant.core.menu.service.DailyMenuService;

@Service
@Transactional(readOnly = true)
public class DailyMenuServiceImpl implements DailyMenuService {

    private final DailyMenuQueryRepository repo;
    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuItemRepository dailyMenuItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final QuotaUsageCounter quotaUsageCounter;
    private final BusinessDateProvider clock;

    public DailyMenuServiceImpl(DailyMenuQueryRepository repo, DailyMenuRepository dailyMenuRepository,
                                DailyMenuItemRepository dailyMenuItemRepository, MenuItemRepository menuItemRepository,
                                RecipeRepository recipeRepository, QuotaUsageCounter quotaUsageCounter, BusinessDateProvider clock) {
        this.repo = repo;
        this.dailyMenuRepository = dailyMenuRepository;
        this.dailyMenuItemRepository = dailyMenuItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.recipeRepository = recipeRepository;
        this.quotaUsageCounter = quotaUsageCounter;
        this.clock = clock;
    }

    /** Thực đơn của ngày kinh doanh hiện tại (04:00 giờ Việt Nam). */
    @Override
    public DailyMenuResponse getTodayDailyMenu() {
        LocalDate date = clock.today();
        return getDailyMenuByDate(date);
    }

    @Override
    public DailyMenuResponse getDailyMenuByDate(LocalDate date) {
        DailyMenuResponse.Header h = repo.findHeaderByDate(date)
                .orElseThrow(() -> ApiException.notFound("Chưa có thực đơn cho ngày " + date));
        return buildDailyMenuResponse(h);
    }

    @Override
    public DailyMenuResponse getDailyMenuById(long id) {
        return buildDailyMenuResponse(getDailyMenuHeader(id));
    }

    /**
     * Tạo thực đơn ngày (DRAFT). Đóng luôn các thực đơn đã công bố của ngày đã qua, vì sau 04:00 chúng không bán được
     * nữa. fromCatalog: đưa sẵn mọi món đang kinh doanh với hạn mức 0, quản lý chỉ cần nhập số suất món bán trong ngày.
     */
    @Override
    @Transactional
    public DailyMenuResponse createDailyMenu(LocalDate businessDate, boolean fromCatalog) {
        dailyMenuRepository.findByStatusAndBusinessDateBefore(DailyMenuStatus.PUBLISHED, clock.today()).forEach(DailyMenu::close);
        DailyMenu menu = dailyMenuRepository.saveAndFlush(new DailyMenu(businessDate));
        if (fromCatalog) {
            for (Object[] row : menuItemRepository.findSellableItemsWithCurrentRecipe()) {
                dailyMenuItemRepository.save(new DailyMenuItem(menu, (MenuItem) row[0], (Recipe) row[1], 0));
            }
            dailyMenuItemRepository.flush();
        }
        return getDailyMenuById(menu.getId());
    }

    /** Gợi ý hạn mức: số suất đã bán của cùng món ở thực đơn nguồn. Chỉ khi thực đơn còn nháp. */
    @Override
    @Transactional
    public FillQuotasResponse fillDailyMenuQuotasFromSales(long dailyMenuId, LocalDate fromDate) {
        DailyMenuResponse.Header target = getDailyMenuHeader(dailyMenuId);
        if (!"DRAFT".equals(target.status())) {
            throw conflictError("MENU_NOT_DRAFT", "Chỉ điền hạn mức khi thực đơn còn nháp");
        }
        DailyMenuResponse.Header source = (fromDate == null ? repo.findPreviousHeader(target.businessDate()) : repo.findHeaderByDate(fromDate))
                .orElseThrow(() -> ApiException.notFound(fromDate == null
                        ? "Chưa có thực đơn nào trước ngày " + target.businessDate()
                        : "Chưa có thực đơn cho ngày " + fromDate));
        if (source.id() == target.id()) {
            throw conflictError("SAME_MENU", "Không điền hạn mức từ chính thực đơn này");
        }
        int updated = fillQuotasFromSoldPortions(target.id(), source.id());
        return new FillQuotasResponse(source.businessDate(), updated, getDailyMenuById(target.id()));
    }

    /** Đổi hạn mức nhiều món: một dòng lỗi (không thuộc thực đơn, thấp hơn số đã bán) thì không đổi dòng nào. */
    @Override
    @Transactional
    public DailyMenuResponse updateDailyMenuQuotas(long dailyMenuId, List<QuotasRequest.Line> lines) {
        requireNotClosed(dailyMenuId);
        for (QuotasRequest.Line l : lines) {
            DailyMenuItem item = dailyMenuItemRepository.findById(l.dailyMenuItemId())
                    .filter(i -> i.getDailyMenu().getId() == dailyMenuId)
                    .orElseThrow(() -> ApiException.notFound("Món " + l.dailyMenuItemId() + " không thuộc thực đơn ngày " + dailyMenuId));
            changeQuotaNotBelowUsed(item, l.quotaTotal());
        }
        dailyMenuItemRepository.flush();
        return getDailyMenuById(dailyMenuId);
    }

    @Override
    public List<IngredientUsageRow> getIngredientUsageOfDailyMenu(long dailyMenuId) {
        DailyMenuResponse.Header h = getDailyMenuHeader(dailyMenuId);
        List<DailyMenuItemResponse> items = repo.findDailyMenuItems(h.id());
        Map<Long, Long> used = quotaUsageCounter.countUsedPortions(items.stream().map(DailyMenuItemResponse::dailyMenuItemId).toList());
        List<Long> soldItemIds = used.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).toList();
        return repo.findIngredientUsage(h.id(), h.businessDate(), soldItemIds);
    }

    @Override
    @Transactional
    public DailyMenuResponse addItemToDailyMenu(long dailyMenuId, DailyMenuItemRequest r) {
        DailyMenu menu = requireNotClosed(dailyMenuId);
        MenuItem menuItem = menuItemRepository.findById(r.menuItemId()).filter(MenuItem::isActive).orElse(null);
        var currentRecipe = recipeRepository.findByMenuItem_IdAndCurrentTrue(r.menuItemId());
        if (menuItem == null || currentRecipe.isEmpty()) {
            throw conflictError("MENU_ITEM_NOT_READY", "Món " + r.menuItemId() + " không tồn tại, đã ngừng kinh doanh hoặc chưa có công thức");
        }
        dailyMenuItemRepository.saveAndFlush(new DailyMenuItem(menu, menuItem, currentRecipe.get(), r.quotaTotal()));
        return getDailyMenuById(dailyMenuId);
    }

    @Override
    @Transactional
    public DailyMenuItemResponse updateDailyMenuItemQuota(long dailyMenuItemId, Integer quotaTotal) {
        DailyMenuItem item = findDailyMenuItemForUpdate(dailyMenuItemId);
        requireNotClosed(item.getDailyMenu().getId());
        changeQuotaNotBelowUsed(item, quotaTotal);
        dailyMenuItemRepository.flush();
        return getDailyMenuItemResponse(dailyMenuItemId);
    }

    /** Chỉ xóa được khi món chưa từng được gọi. */
    @Override
    @Transactional
    public void removeItemFromDailyMenu(long dailyMenuItemId) {
        DailyMenuItem item = findDailyMenuItemForUpdate(dailyMenuItemId);
        requireNotClosed(item.getDailyMenu().getId());
        if (quotaUsageCounter.hasBeenOrdered(dailyMenuItemId)) {
            throw ApiException.invalidState("Món " + item.getMenuItem().getName() + " đã có khách gọi: không xóa khỏi thực đơn, hãy tạm dừng");
        }
        dailyMenuItemRepository.delete(item);
        dailyMenuItemRepository.flush();
    }

    @Override
    @Transactional
    public DailyMenuResponse publishDailyMenu(long id) {
        DailyMenu menu = findMenuForUpdate(id);
        if (menu.getStatus() != DailyMenuStatus.DRAFT) {
            throw ApiException.invalidState("Thực đơn ngày " + id + " đang " + menu.getStatus() + ": chỉ công bố thực đơn nháp");
        }
        if (!dailyMenuItemRepository.existsByDailyMenuId(id)) {
            throw ApiException.invalidState("Không công bố thực đơn ngày chưa có món nào");
        }
        menu.publish(clock.now());
        dailyMenuRepository.flush();
        return getDailyMenuById(id);
    }

    @Override
    @Transactional
    public DailyMenuResponse closeDailyMenu(long id) {
        DailyMenu menu = findMenuForUpdate(id);
        if (menu.getStatus() != DailyMenuStatus.PUBLISHED) {
            throw ApiException.invalidState("Thực đơn ngày " + id + " đang " + menu.getStatus() + ": chỉ đóng thực đơn đang bán");
        }
        menu.close();
        dailyMenuRepository.flush();
        return getDailyMenuById(id);
    }

    @Override
    @Transactional
    public DailyMenuItemResponse pauseDailyMenuItem(long dailyMenuItemId, long actorId, String reason) {
        DailyMenuItem dish = findDailyMenuItemForUpdate(dailyMenuItemId);
        requireNotClosed(dish.getDailyMenu().getId());
        if (dish.isPaused()) {
            throw conflictError("ALREADY_PAUSED", "Món " + dish.getMenuItem().getName() + " đang tạm dừng");
        }
        dish.pause(reason, actorId, clock.now());
        dailyMenuItemRepository.flush();
        return getDailyMenuItemResponse(dailyMenuItemId);
    }

    @Override
    @Transactional
    public DailyMenuItemResponse resumeDailyMenuItem(long dailyMenuItemId) {
        DailyMenuItem dish = findDailyMenuItemForUpdate(dailyMenuItemId);
        requireNotClosed(dish.getDailyMenu().getId());
        if (!dish.isPaused()) {
            throw conflictError("NOT_PAUSED", "Món " + dish.getMenuItem().getName() + " không tạm dừng");
        }
        dish.resume();
        dailyMenuItemRepository.flush();
        return getDailyMenuItemResponse(dailyMenuItemId);
    }

    /** Thực đơn đã đóng là số liệu đã chốt của ngày (tóm tắt cuối ngày dựa vào nó): không sửa món, hạn mức, tạm dừng nữa. */
    private DailyMenu requireNotClosed(long dailyMenuId) {
        DailyMenu menu = dailyMenuRepository.findById(dailyMenuId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thực đơn ngày " + dailyMenuId));
        if (menu.isClosed()) {
            throw conflictError("MENU_CLOSED", "Thực đơn ngày đã đóng");
        }
        return menu;
    }

    private DailyMenu findMenuForUpdate(long id) {
        return dailyMenuRepository.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thực đơn ngày " + id));
    }

    private DailyMenuItem findDailyMenuItemForUpdate(long dailyMenuItemId) {
        return dailyMenuItemRepository.findByIdForUpdate(dailyMenuItemId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy món trong thực đơn ngày " + dailyMenuItemId));
    }

    /** Hạn mức mới không được thấp hơn số suất bếp đã nhận (null = không giới hạn). */
    private void changeQuotaNotBelowUsed(DailyMenuItem item, Integer quotaTotal) {
        if (quotaTotal != null) {
            long used = quotaUsageCounter.countUsedPortions(item.getId());
            if (quotaTotal >= 0 && quotaTotal < used) {
                throw ApiException.invalidState("Hạn mức " + quotaTotal + " thấp hơn số suất đã nhận " + used);
            }
        }
        item.changeQuota(quotaTotal);
    }

    private DailyMenuItemResponse getDailyMenuItemResponse(long id) {
        return repo.findDailyMenuItem(id).map(item -> withQuotaUsage(List.of(item)).getFirst()).orElseThrow(() -> ApiException.notFound("Không tìm thấy món trong thực đơn ngày " + id));
    }

    private DailyMenuResponse.Header getDailyMenuHeader(long id) {
        return repo.findDailyMenuHeader(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thực đơn ngày " + id));
    }

    private DailyMenuResponse buildDailyMenuResponse(DailyMenuResponse.Header h) {
        return new DailyMenuResponse(h.id(), h.businessDate(), h.status(), h.publishedAt(), withQuotaUsage(repo.findDailyMenuItems(h.id())));
    }

    private static ApiException conflictError(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** Hạn mức = số suất đã dùng của cùng món ở thực đơn nguồn. Món không có ở thực đơn nguồn giữ nguyên. */
    private int fillQuotasFromSoldPortions(long targetMenuId, long sourceMenuId) {
        Map<Long, Long> sourceItemByMenuItem = dailyMenuItemRepository.findByDailyMenuId(sourceMenuId).stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.getMenuItem().getId(), DailyMenuItem::getId));
        Map<Long, Long> usedBySourceItem = quotaUsageCounter.countUsedPortions(sourceItemByMenuItem.values());
        int updated = 0;
        for (DailyMenuItem target : dailyMenuItemRepository.findByDailyMenuId(targetMenuId)) {
            Long sourceItemId = sourceItemByMenuItem.get(target.getMenuItem().getId());
            if (sourceItemId != null) {
                changeQuotaNotBelowUsed(target, usedBySourceItem.get(sourceItemId).intValue());
                updated++;
            }
        }
        dailyMenuItemRepository.flush();
        return updated;
    }

    /** Gắn số suất đã dùng và số suất còn lại (null = không giới hạn) vào từng món. */
    private List<DailyMenuItemResponse> withQuotaUsage(List<DailyMenuItemResponse> items) {
        Map<Long, Long> used = quotaUsageCounter.countUsedPortions(items.stream().map(DailyMenuItemResponse::dailyMenuItemId).toList());
        return items.stream().map(i -> {
            long usedPortions = used.get(i.dailyMenuItemId());
            Long remaining = i.quotaTotal() == null ? null : Math.max(i.quotaTotal() - usedPortions, 0);
            return new DailyMenuItemResponse(i.dailyMenuItemId(), i.dailyMenuId(), i.menuItemId(), i.name(), i.categoryName(), i.price(),
                    i.recipeId(), i.recipeVersion(), i.quotaTotal(), usedPortions, remaining, i.paused(), i.pauseReason(), i.menuItemActive());
        }).toList();
    }
}
