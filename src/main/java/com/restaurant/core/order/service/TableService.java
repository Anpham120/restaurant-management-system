package com.restaurant.core.order.service;

import java.util.List;

import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.order.dto.response.AbandonResponse;
import com.restaurant.core.order.dto.response.OpenSessionResponse;
import com.restaurant.core.order.dto.response.SessionResponse;
import com.restaurant.core.order.dto.request.TableRequest;
import com.restaurant.core.order.dto.response.TableResponse;

/** Bàn và phiên phục vụ. actorId luôn là CurrentUser.employeeId(). Owner: Phạm Duy An. */
public interface TableService {

    List<TableResponse> getAllTables(boolean includeInactive);

    TableResponse getTableById(long tableId);

    TableResponse createTable(TableRequest request);

    TableResponse updateTable(long tableId, TableRequest request);

    /** Ngừng dùng bàn trống (không xóa vì bàn có lịch sử phiên). Bàn đang có khách → 409. */
    TableResponse deactivateTable(long tableId);

    TableResponse activateTable(long tableId);

    /** Mở phiên cho bàn trống: tạo phiên + đơn hàng, bàn chuyển OCCUPIED. */
    OpenSessionResponse openTableSession(long tableId, long actorId);

    SessionResponse getTableSessionById(long sessionId);

    PageResponse<SessionResponse> getTableSessionHistory(long tableId, int page, int size);

    /** Đóng phiên không phát sinh tiền (mọi món đã hủy/bị từ chối, chưa có hóa đơn): đơn CANCELLED, trả bàn. */
    SessionResponse closeEmptyTableSession(long sessionId, long actorId);

    /** Khách bỏ về (Quản lý): hủy món chưa phục vụ, hóa đơn UNCOLLECTED hoặc WAIVED nếu 0 đồng, trả bàn. */
    AbandonResponse recordCustomerLeftWithoutPaying(long sessionId, long actorId, String reason);
}
