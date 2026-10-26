package com.restaurant.core.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.order.dto.SessionResponse;
import com.restaurant.core.order.dto.TableResponse;

/**
 * Bàn và phiên phục vụ. Ghi vào bảng nhạy cảm (table_sessions, orders, dining_tables.status…) CHỈ qua hàm rc_* —
 * role DB rc_app không có quyền INSERT/UPDATE trực tiếp các bảng này (docs/09-acl-v4.1.sql).
 */
@Repository
public class TableRepository {

    private static final String TABLE_SELECT = """
            SELECT t.id, t.code, t.capacity, t.status, s.id AS current_session_id, o.id AS current_order_id
              FROM dining_tables t
              LEFT JOIN table_sessions s ON s.dining_table_id = t.id AND s.status = 'OPEN'
              LEFT JOIN orders o ON o.table_session_id = s.id
            """;

    static final String SESSION_SELECT = """
            SELECT s.id AS session_id, t.id AS table_id, t.code AS table_code, s.status, s.opened_at, s.closed_at,
                   s.opened_by_id, s.closed_by_id, o.id AS order_id, o.status AS order_status,
                   i.id AS invoice_id, i.status AS invoice_status, i.total AS invoice_total
              FROM table_sessions s
              JOIN dining_tables t ON t.id = s.dining_table_id
              JOIN orders o ON o.table_session_id = s.id
              LEFT JOIN invoices i ON i.order_id = o.id
            """;

    private final JdbcClient jdbc;

    public TableRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<TableResponse> findActiveTables() {
        return jdbc.sql(TABLE_SELECT + " WHERE t.is_active ORDER BY t.code").query(TableResponse.class).list();
    }

    public Optional<TableResponse> findTable(long tableId) {
        return jdbc.sql(TABLE_SELECT + " WHERE t.id = :id").param("id", tableId).query(TableResponse.class).optional();
    }

    public Optional<SessionResponse> findSession(long sessionId) {
        return jdbc.sql(SESSION_SELECT + " WHERE s.id = :id").param("id", sessionId).query(SessionResponse.class).optional();
    }

    public List<SessionResponse> findSessionsOfTable(long tableId, int page, int size) {
        return jdbc.sql(SESSION_SELECT + " WHERE s.dining_table_id = :t ORDER BY s.opened_at DESC, s.id DESC LIMIT :size OFFSET :offset")
                .param("t", tableId).param("size", size).param("offset", (long) page * size)
                .query(SessionResponse.class).list();
    }

    public long countSessionsOfTable(long tableId) {
        return jdbc.sql("SELECT count(*) FROM table_sessions WHERE dining_table_id = :t").param("t", tableId).query(Long.class).single();
    }

    /** rc_open_session(p_table, p_actor) → id phiên. Một câu lệnh = một transaction nguyên tử của PostgreSQL. */
    public long openSession(long tableId, long actorId) {
        return jdbc.sql("SELECT rc_open_session(:tableId, :actorId)")
                .param("tableId", tableId)
                .param("actorId", actorId)
                .query(Long.class)
                .single();
    }

    /** rc_close_empty_session(p_session, p_actor): phiên không có món phải thu và chưa có hóa đơn → đơn CANCELLED, trả bàn. */
    public void closeEmptySession(long sessionId, long actorId) {
        jdbc.sql("SELECT rc_close_empty_session(:s, :a)").param("s", sessionId).param("a", actorId).query().singleRow();
    }

    /** rc_abandon_session(p_session, p_actor, p_reason) → id hóa đơn UNCOLLECTED/WAIVED, hoặc NULL nếu không có nghĩa vụ. */
    public Optional<Long> abandonSession(long sessionId, long actorId, String reason) {
        return jdbc.sql("SELECT rc_abandon_session(:s, :a, :r)").param("s", sessionId).param("a", actorId).param("r", reason)
                .query(Long.class).optional();
    }
}
