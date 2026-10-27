package com.restaurant.core.menu.service;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.menu.dto.DailyMenuItemRequest;
import com.restaurant.core.menu.dto.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.DailyMenuResponse;
import com.restaurant.core.menu.repository.DailyMenuRepository;

/**
 * Thực đơn ngày, hạn mức, tạm dừng món. {@link #today()} là hợp đồng cung cấp món cho module Order (Phạm Duy An):
 * màn hình gọi món dùng dailyMenuItemId trả về ở đây. Owner: Lê Anh.
 */
@Service
public class DailyMenuService {

    private final DailyMenuRepository repo;

    public DailyMenuService(DailyMenuRepository repo) {
        this.repo = repo;
    }

    /** Thực đơn của ngày kinh doanh hiện tại (04:00 giờ Việt Nam). */
    public DailyMenuResponse today() {
        LocalDate date = repo.currentBusinessDate();
        return byDate(date);
    }

    public DailyMenuResponse byDate(LocalDate date) {
        DailyMenuResponse.Header h = repo.findHeaderByDate(date)
                .orElseThrow(() -> MenuCatalogService.notFound("Chưa có thực đơn cho ngày " + date));
        return withItems(h);
    }

    public DailyMenuResponse get(long id) {
        return withItems(header(id));
    }

    public DailyMenuResponse create(LocalDate businessDate) {
        return get(repo.insertMenu(businessDate));
    }

    public DailyMenuResponse addItem(long dailyMenuId, DailyMenuItemRequest r) {
        if ("CLOSED".equals(header(dailyMenuId).status())) {
            throw conflict("MENU_CLOSED", "Thực đơn ngày đã đóng");
        }
        repo.insertItem(dailyMenuId, r.menuItemId(), r.quotaTotal())
                .orElseThrow(() -> conflict("MENU_ITEM_NOT_READY", "Món " + r.menuItemId() + " không tồn tại, đã ngừng kinh doanh hoặc chưa có công thức"));
        return get(dailyMenuId);
    }

    public DailyMenuItemResponse updateQuota(long dailyMenuItemId, Integer quotaTotal) {
        if (repo.updateQuota(dailyMenuItemId, quotaTotal) == 0) {
            throw MenuCatalogService.notFound("Không tìm thấy món trong thực đơn ngày " + dailyMenuItemId);
        }
        return item(dailyMenuItemId);
    }

    /** Chỉ xóa được khi chưa có món gọi (trigger DB → RC409). */
    public void removeItem(long dailyMenuItemId) {
        if (repo.deleteItem(dailyMenuItemId) == 0) {
            throw MenuCatalogService.notFound("Không tìm thấy món trong thực đơn ngày " + dailyMenuItemId);
        }
    }

    public DailyMenuResponse publish(long id) {
        if (repo.publish(id) == 0) {
            throw MenuCatalogService.notFound("Không tìm thấy thực đơn ngày " + id);
        }
        return get(id);
    }

    public DailyMenuResponse close(long id) {
        if (repo.close(id) == 0) {
            throw MenuCatalogService.notFound("Không tìm thấy thực đơn ngày " + id);
        }
        return get(id);
    }

    public DailyMenuItemResponse pause(long dailyMenuItemId, long actorId, String reason) {
        DailyMenuItemResponse current = item(dailyMenuItemId);
        if (repo.pause(dailyMenuItemId, actorId, reason) == 0) {
            throw conflict("ALREADY_PAUSED", "Món " + current.name() + " đang tạm dừng");
        }
        return item(dailyMenuItemId);
    }

    public DailyMenuItemResponse resume(long dailyMenuItemId) {
        DailyMenuItemResponse current = item(dailyMenuItemId);
        if (repo.resume(dailyMenuItemId) == 0) {
            throw conflict("NOT_PAUSED", "Món " + current.name() + " không tạm dừng");
        }
        return item(dailyMenuItemId);
    }

    private DailyMenuItemResponse item(long id) {
        return repo.findItem(id).orElseThrow(() -> MenuCatalogService.notFound("Không tìm thấy món trong thực đơn ngày " + id));
    }

    private DailyMenuResponse.Header header(long id) {
        return repo.findHeader(id).orElseThrow(() -> MenuCatalogService.notFound("Không tìm thấy thực đơn ngày " + id));
    }

    private DailyMenuResponse withItems(DailyMenuResponse.Header h) {
        return new DailyMenuResponse(h.id(), h.businessDate(), h.status(), h.publishedAt(), repo.findItems(h.id()));
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
