package vn.bnn.rms.payment.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.bnn.rms.config.AppProperties;
import vn.bnn.rms.payment.dto.PaymentDtos.SepayWebhookRequest;
import vn.bnn.rms.payment.service.SepayWebhookMonitor;
import vn.bnn.rms.payment.service.SepayWebhookService;

/**
 * SePay calls this for every bank movement. It counts as delivered only on 200 with {"success": true}; every answer
 * is also counted by {@link SepayWebhookMonitor} (BR-32).
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class SepayWebhookController {

    private final SepayWebhookService webhookService;
    private final SepayWebhookMonitor monitor;
    private final AppProperties props;

    @PostMapping("/sepay")
    public ResponseEntity<Map<String, Boolean>> receive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookRequest request) {
        if (!hasValidApiKey(authorization)) {
            monitor.failed("Sai khoá API");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        if (request.id() == null) {
            monitor.failed("Thiếu mã giao dịch");
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        try {
            webhookService.handle(request);
        } catch (DataIntegrityViolationException duplicate) {
            // Same transaction delivered twice at the same moment; the other delivery already stored it.
        } catch (RuntimeException e) {
            monitor.failed("Lỗi khi xử lý giao dịch");
            throw e;
        }
        monitor.succeeded();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** NFR-04: constant-time comparison of "Apikey <key>". */
    private boolean hasValidApiKey(String authorization) {
        if (authorization == null) {
            return false;
        }
        byte[] expected = ("Apikey " + props.sepay().apiKey()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, authorization.trim().getBytes(StandardCharsets.UTF_8));
    }
}
