package com.restaurant.core.menu.service;

import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.menu.dto.request.DailyMenuItemRequest;
import com.restaurant.core.menu.dto.response.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.response.DailyMenuResponse;
import com.restaurant.core.menu.dto.response.FillQuotasResponse;
import com.restaurant.core.menu.dto.response.IngredientUsageRow;
import com.restaurant.core.menu.dto.request.QuotasRequest;

/**
 * Thực đơn ngày, hạn mức, tạm dừng món. {@link #getTodayDailyMenu()} là hợp đồng cung cấp món cho module Order (Phạm Duy An):
 * màn hình gọi món dùng dailyMenuItemId trả về ở đây. Owner: Lê Anh.
 */
public interface DailyMenuService {

    /** Thực đơn của ngày kinh doanh hiện tại (04:00 giờ Việt Nam). */
    DailyMenuResponse getTodayDailyMenu();

    DailyMenuResponse getDailyMenuByDate(LocalDate date);

    DailyMenuResponse getDailyMenuById(long id);

    /**
     * Tạo thực đơn ngày (DRAFT). Đóng luôn các thực đơn đã công bố của ngày đã qua, vì sau 04:00 chúng không bán được
     * nữa. fromCatalog: đưa sẵn mọi món đang kinh doanh với hạn mức 0, quản lý chỉ cần nhập số suất món bán trong ngày.
     */
    DailyMenuResponse createDailyMenu(LocalDate businessDate, boolean fromCatalog);

    /** Gợi ý hạn mức: số suất đã bán của cùng món ở thực đơn nguồn. Chỉ khi thực đơn còn nháp. */
    FillQuotasResponse fillDailyMenuQuotasFromSales(long dailyMenuId, LocalDate fromDate);

    /** Đổi hạn mức nhiều món: một dòng lỗi (không thuộc thực đơn, thấp hơn số đã bán) thì không đổi dòng nào. */
    DailyMenuResponse updateDailyMenuQuotas(long dailyMenuId, List<QuotasRequest.Line> lines);

    List<IngredientUsageRow> getIngredientUsageOfDailyMenu(long dailyMenuId);

    DailyMenuResponse addItemToDailyMenu(long dailyMenuId, DailyMenuItemRequest r);

    DailyMenuItemResponse updateDailyMenuItemQuota(long dailyMenuItemId, Integer quotaTotal);

    /** Chỉ xóa được khi chưa có món gọi (trigger DB → RC409). */
    void removeItemFromDailyMenu(long dailyMenuItemId);

    DailyMenuResponse publishDailyMenu(long id);

    DailyMenuResponse closeDailyMenu(long id);

    DailyMenuItemResponse pauseDailyMenuItem(long dailyMenuItemId, long actorId, String reason);

    DailyMenuItemResponse resumeDailyMenuItem(long dailyMenuItemId);
}
