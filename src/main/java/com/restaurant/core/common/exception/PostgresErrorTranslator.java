package com.restaurant.core.common.exception;

import java.sql.SQLException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Chuyển lỗi PostgreSQL (SQLSTATE) thành lỗi API. Mã RC* do hàm rc_raise trong docs/04-schema-v4.1.sql phát ra:
 * RC001 thiếu nguyên liệu · RC403 không đủ vai trò · RC409 xung đột trạng thái · RC422 dữ liệu không hợp lệ ·
 * RC500 bất biến bị phá (kiểm lúc COMMIT). Thông điệp RC4xx/RC001 của DB viết cho người dùng nên được trả nguyên văn;
 * các lỗi còn lại chỉ trả thông điệp chung (chi tiết nằm trong log).
 */
@Component
public class PostgresErrorTranslator {

    public record Translated(HttpStatus status, String code, String message, boolean exposeDbMessage) {
    }

    /** Trả null nếu không tìm thấy SQLException trong chuỗi nguyên nhân. */
    public Translated translate(Throwable error) {
        SQLException sql = findSqlException(error);
        if (sql == null || sql.getSQLState() == null) {
            return null;
        }
        String state = sql.getSQLState();
        String dbMessage = cleanMessage(sql.getMessage());
        return switch (state) {
            case "RC001" -> new Translated(HttpStatus.CONFLICT, "RC001", dbMessage, true);
            case "RC403" -> new Translated(HttpStatus.FORBIDDEN, "RC403", dbMessage, true);
            case "RC409" -> new Translated(HttpStatus.CONFLICT, "RC409", dbMessage, true);
            case "RC422" -> new Translated(HttpStatus.UNPROCESSABLE_CONTENT, "RC422", dbMessage, true);
            case "RC500" -> new Translated(HttpStatus.INTERNAL_SERVER_ERROR, "RC500", "Dữ liệu vi phạm bất biến hệ thống", false);
            case "23505" -> new Translated(HttpStatus.CONFLICT, "DUPLICATE", "Dữ liệu đã tồn tại", false);
            case "23P01" -> new Translated(HttpStatus.CONFLICT, "OVERLAP", "Trùng khoảng thời gian với bản ghi khác", false);
            case "23503" -> new Translated(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCE_NOT_FOUND", "Dữ liệu tham chiếu không tồn tại hoặc đang được dùng", false);
            case "23514", "23502", "22P02", "22003" ->
                    new Translated(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DATA", "Dữ liệu không thỏa ràng buộc", false);
            case "40001", "40P01" -> new Translated(HttpStatus.SERVICE_UNAVAILABLE, "RETRY", "Hệ thống đang bận, hãy thử lại", false);
            case "42501" -> new Translated(HttpStatus.INTERNAL_SERVER_ERROR, "DB_PERMISSION", "Tài khoản database không có quyền thao tác này", false);
            default -> null;
        };
    }

    private static SQLException findSqlException(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql;
            }
        }
        return null;
    }

    /** "ERROR: phiên 5 không mở\n  Where: PL/pgSQL ..." → "phiên 5 không mở". */
    static String cleanMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String first = raw.lines().findFirst().orElse("").strip();
        return first.startsWith("ERROR:") ? first.substring("ERROR:".length()).strip() : first;
    }
}
