package com.restaurant.core.finance.sepay;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.restaurant.core.common.exception.ApiException;

/**
 * Xử lý webhook SePay (https://docs.sepay.vn/tich-hop-webhooks.html):
 * 1. Xác thực "Authorization: Apikey &lt;key&gt;" (cách xác thực SePay mô tả cho webhook; so sánh thời gian hằng).
 * 2. Chỉ xử lý giao dịch tiền vào (transferType = "in").
 * 3. Mã tham chiếu lấy từ "code" (SePay nhận diện theo cấu hình mã thanh toán) hoặc tìm mẫu RC&lt;hóa đơn&gt;Q&lt;n&gt; trong "content".
 * 4. Gọi rc_process_verified_qr với id giao dịch SePay làm khóa chống trùng; DB đối chiếu số tiền, tham chiếu, trạng thái.
 * Mọi kết quả nghiệp vụ (kể cả MISMATCH, AFTER_CANCEL, gửi lặp) đều trả success để SePay không gửi lại; chỉ lỗi xác thực,
 * payload hỏng hoặc lỗi hệ thống mới trả mã lỗi (SePay sẽ retry).
 */
@Service
public class SepayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(SepayWebhookService.class);
    private static final Pattern REFERENCE = Pattern.compile("RC\\d+Q\\d+", Pattern.CASE_INSENSITIVE);

    public record WebhookResult(String transactionCode, String result, String reference) {
    }

    private final SepayProperties props;
    private final SepayWebhookRepository repository;
    private final JsonMapper json;

    public SepayWebhookService(SepayProperties props, SepayWebhookRepository repository, JsonMapper json) {
        this.props = props;
        this.repository = repository;
        this.json = json;
    }

    public WebhookResult handle(String authorizationHeader, String rawBody) {
        verifyApiKey(authorizationHeader);
        JsonNode body;
        try {
            body = json.readTree(rawBody);
        } catch (JacksonException e) {
            throw badPayload("Payload không phải JSON");
        }
        JsonNode id = body.get("id");
        JsonNode amount = body.get("transferAmount");
        if (id == null || id.isNull() || amount == null || !amount.isNumber()) {
            throw badPayload("Payload thiếu id hoặc transferAmount");
        }
        String transactionCode = id.asString();
        if (!"in".equals(text(body, "transferType"))) {
            log.info("SePay giao dịch {} không phải tiền vào: bỏ qua", transactionCode);
            return new WebhookResult(transactionCode, "IGNORED_OUTGOING", null);
        }
        String reference = extractReference(text(body, "code"), text(body, "content"));
        BigDecimal transferAmount = amount.decimalValue();
        String result = repository.processVerifiedQr(transactionCode, reference, transferAmount, rawBody);
        log.info("SePay giao dịch {} tham chiếu {} → {}", transactionCode, reference, result);
        return new WebhookResult(transactionCode, result, reference);
    }

    private void verifyApiKey(String header) {
        if (!SepayProperties.hasText(props.apiKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Chưa cấu hình SEPAY_API_KEY");
        }
        String expected = "Apikey " + props.apiKey();
        if (header == null || !MessageDigest.isEqual(header.strip().getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Webhook không hợp lệ");
        }
    }

    /** "" khi không tìm được — DB ghi MISMATCH và mở sự cố đối soát, không tạo payment. */
    static String extractReference(String code, String content) {
        if (code != null && REFERENCE.matcher(code).matches()) {
            return code.toUpperCase();
        }
        if (content != null) {
            Matcher m = REFERENCE.matcher(content);
            if (m.find()) {
                return m.group().toUpperCase();
            }
        }
        return code != null ? code : "";
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    private static ApiException badPayload(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
