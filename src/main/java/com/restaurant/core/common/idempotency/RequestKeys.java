package com.restaurant.core.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.restaurant.core.common.exception.ApiException;

/**
 * Chống tạo trùng khi client gửi lặp (mạng chập chờn, bấm hai lần): mỗi lần bấm "Tạo" có một requestKey.
 * Hợp đồng chung cho mọi chứng từ (phiếu kho, sơ chế, hoàn trả, hủy hàng, khoản chi, lượt gọi món):
 * <ol>
 * <li>{@link #lockRequestKey}: khóa theo (bảng, requestKey) đến hết transaction, hai yêu cầu cùng khóa chạy lần lượt;</li>
 * <li>đọc lại theo requestKey sau khi khóa: đã có → {@link #requireSameRequest} rồi trả chứng từ cũ;</li>
 * <li>chưa có → tạo mới, lưu kèm {@link #fingerprintOf dấu vân tay} nội dung.</li>
 * </ol>
 * Cùng khóa + cùng nội dung → trả chứng từ đã tạo (không lỗi trùng khóa); cùng khóa khác nội dung → 409.
 */
@Component
@RequiredArgsConstructor
public class RequestKeys {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    /** Dấu vân tay (SHA-256, 64 ký tự hex) của nội dung yêu cầu. */
    public String fingerprintOf(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Khóa theo (bảng, requestKey) đến hết transaction: hai yêu cầu cùng khóa đến cùng lúc thì chạy lần lượt. */
    public void lockRequestKey(String table, String requestKey) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:type), hashtext(:key))").param("type", table).param("key", requestKey)
                .query().singleRow();
    }

    /** storedFingerprint null = chứng từ tạo trước khi có cột request_hash: không so được, chấp nhận như gửi lặp. */
    public void requireSameRequest(String storedFingerprint, String fingerprint, String requestKey, String documentName) {
        if (storedFingerprint != null && !storedFingerprint.equals(fingerprint)) {
            throw ApiException.invalidState("requestKey " + requestKey + " đã dùng cho một " + documentName + " khác nội dung");
        }
    }
}
