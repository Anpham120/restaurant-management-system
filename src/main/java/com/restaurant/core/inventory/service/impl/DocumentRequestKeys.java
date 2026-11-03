package com.restaurant.core.inventory.service.impl;

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
 * Chống tạo trùng chứng từ kho khi client gửi lặp: mỗi lần bấm "Tạo phiếu" có một requestKey. Cùng khóa + cùng nội dung
 * → trả phiếu đã tạo; cùng khóa khác nội dung → 409.
 */
@Component
@RequiredArgsConstructor
public class DocumentRequestKeys {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    /** Dấu vân tay (SHA-256) của nội dung yêu cầu. */
    public String fingerprintOf(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Khóa theo (loại phiếu, requestKey) đến hết transaction: hai yêu cầu cùng khóa đến cùng lúc thì chạy lần lượt. */
    public void lockRequestKey(String documentType, String requestKey) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:type), hashtext(:key))").param("type", documentType).param("key", requestKey)
                .query().singleRow();
    }

    public void requireSameRequest(String storedFingerprint, String fingerprint, String requestKey, String documentName) {
        if (!storedFingerprint.equals(fingerprint)) {
            throw ApiException.invalidState("requestKey " + requestKey + " đã dùng cho một " + documentName + " khác nội dung");
        }
    }
}
