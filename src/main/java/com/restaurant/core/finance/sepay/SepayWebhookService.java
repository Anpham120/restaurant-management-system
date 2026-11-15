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
import com.restaurant.core.finance.service.impl.QrTransferProcessor;

/**
 * Xử lý webhook SePay (https://docs.sepay.vn/tich-hop-webhooks.html):
 * 1. Xác thực "Authorization: Apikey &lt;key&gt;" (cách xác thực SePay mô tả cho webhook; so sánh thời gian hằng).
 * 2. Chỉ xử lý giao dịch tiền vào (transferType = "in").
 * 3. Mã tham chiếu lấy từ "code" (SePay nhận diện theo cấu hình mã thanh toán) hoặc tìm mẫu RC&lt;hóa đơn&gt;Q&lt;n&gt; trong "content".
 * 4. QrTransferProcessor ghi nhận với id giao dịch SePay làm khóa chống trùng; đối chiếu số tiền, tham chiếu, trạng thái.
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
    private final QrTransferProcessor qrTransferProcessor;
    private final JsonMapper json;

    public SepayWebhookService(SepayProperties props, QrTransferProcessor qrTransferProcessor, JsonMapper json) {
        this.props = props;
        this.qrTransferProcessor = qrTransferProcessor;
        this.json = json;
    }

    public WebhookResult receiveTransferWebhook(String authorizationHeader, String rawBody) {
        verifyApiKey(authorizationHeader);
        JsonNode body;
        try {
            body = json.readTree(rawBody);
        } catch (JacksonException e) {
            throw badPayloadError("Payload không phải JSON");
        }
        JsonNode id = body.get("id");
        JsonNode amount = body.get("transferAmount");
        if (id == null || id.isNull() || amount == null || !amount.isNumber()) {
            throw badPayloadError("Payload thiếu id hoặc transferAmount");
        }
        String transactionCode = id.asString();
        if (!"in".equals(readTextField(body, "transferType"))) {
            log.info("SePay giao dịch {} không phải tiền vào: bỏ qua", transactionCode);
            return new WebhookResult(transactionCode, "IGNORED_OUTGOING", null);
        }
        String reference = extractReference(readTextField(body, "code"), readTextField(body, "content"));
        BigDecimal transferAmount = amount.decimalValue();
        boolean toRestaurantAccount = isRestaurantAccount(readTextField(body, "accountNumber"), readTextField(body, "subAccount"));
        if (!toRestaurantAccount) {
            log.warn("SePay giao dịch {} vào tài khoản khác tài khoản nhận của nhà hàng: ghi MISMATCH để đối soát", transactionCode);
        }
        String result = qrTransferProcessor.recordVerifiedTransfer(transactionCode, reference, transferAmount, toRestaurantAccount, rawBody).name();
        log.info("SePay giao dịch {} tham chiếu {} → {}", transactionCode, reference, result);
        return new WebhookResult(transactionCode, result, reference);
    }

    /**
     * Tiền phải vào đúng tài khoản in trên ảnh QR (SEPAY_BANK_ACCOUNT). Một tài khoản SePay có thể nhận thông báo từ nhiều
     * tài khoản ngân hàng: thiếu bước này, một giao dịch đúng mã và đúng số tiền ở tài khoản khác vẫn được tính là đã thu.
     * Tài khoản ảo (VA) đến trong "subAccount", tài khoản chính trong "accountNumber": khớp một trong hai là đủ.
     */
    boolean isRestaurantAccount(String accountNumber, String subAccount) {
        String expected = props.bankAccount();
        if (!SepayProperties.hasText(expected)) {
            return false;                                   // chưa cấu hình tài khoản nhận: không có QR nào hợp lệ
        }
        return expected.strip().equals(accountNumber == null ? null : accountNumber.strip())
                || expected.strip().equals(subAccount == null ? null : subAccount.strip());
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

    private static String readTextField(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    private static ApiException badPayloadError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
