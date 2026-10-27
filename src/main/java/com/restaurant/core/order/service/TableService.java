package com.restaurant.core.order.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.order.dto.AbandonResponse;
import com.restaurant.core.order.dto.OpenSessionResponse;
import com.restaurant.core.order.dto.PageResponse;
import com.restaurant.core.order.dto.SessionResponse;
import com.restaurant.core.order.dto.TableRequest;
import com.restaurant.core.order.dto.TableResponse;
import com.restaurant.core.order.repository.OrderRepository;
import com.restaurant.core.order.repository.TableRepository;

/** Bàn và phiên phục vụ. Mỗi thao tác ghi là MỘT lời gọi rc_* (không cần @Transactional). */
@Service
public class TableService {

    private final TableRepository tables;
    private final OrderRepository orders;

    public TableService(TableRepository tables, OrderRepository orders) {
        this.tables = tables;
        this.orders = orders;
    }

    public List<TableResponse> listTables(boolean includeInactive) {
        return tables.findTables(includeInactive);
    }

    /** Thêm bàn (Manager). Trùng mã → 23505 → 409 DUPLICATE. */
    public TableResponse createTable(TableRequest request) {
        return getTable(tables.insertTable(request.code().strip(), request.capacity()));
    }

    public TableResponse updateTable(long tableId, TableRequest request) {
        if (tables.updateTable(tableId, request.code().strip(), request.capacity()) == 0) {
            throw notFound("Không tìm thấy bàn " + tableId);
        }
        return getTable(tableId);
    }

    /** Ngừng sử dụng bàn trống (không xóa — bàn có lịch sử phiên). Bàn đang có khách → 409. */
    public TableResponse deactivateTable(long tableId) {
        TableResponse table = getTable(tableId);
        if (tables.deactivateIfAvailable(tableId) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "TABLE_OCCUPIED", "Bàn " + table.code() + " đang có khách: đóng phiên trước khi ngừng sử dụng");
        }
        return getTable(tableId);
    }

    public TableResponse activateTable(long tableId) {
        if (tables.activate(tableId) == 0) {
            throw notFound("Không tìm thấy bàn " + tableId);
        }
        return getTable(tableId);
    }

    public TableResponse getTable(long tableId) {
        return tables.findTable(tableId).orElseThrow(() -> notFound("Không tìm thấy bàn " + tableId));
    }

    public OpenSessionResponse openSession(long tableId, long actorId) {
        long sessionId = tables.openSession(tableId, actorId);
        return new OpenSessionResponse(sessionId, tableId, orders.findOrderIdOfSession(sessionId));
    }

    public SessionResponse getSession(long sessionId) {
        return tables.findSession(sessionId).orElseThrow(() -> notFound("Không tìm thấy phiên " + sessionId));
    }

    public PageResponse<SessionResponse> sessionHistory(long tableId, int page, int size) {
        getTable(tableId);
        return new PageResponse<>(tables.findSessionsOfTable(tableId, page, size), page, size, tables.countSessionsOfTable(tableId));
    }

    /** Đóng phiên không phát sinh nghĩa vụ thu (mọi món CANCELLED/REJECTED, chưa có hóa đơn). */
    public SessionResponse closeEmptySession(long sessionId, long actorId) {
        tables.closeEmptySession(sessionId, actorId);
        return getSession(sessionId);
    }

    /** Khách bỏ về (Manager): hóa đơn UNCOLLECTED, hoặc WAIVED nếu 0 đồng; đóng phiên, trả bàn. */
    public AbandonResponse abandonSession(long sessionId, long actorId, String reason) {
        Long invoiceId = tables.abandonSession(sessionId, actorId, reason).orElse(null);
        return new AbandonResponse(sessionId, invoiceId, getSession(sessionId).invoiceStatus());
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
