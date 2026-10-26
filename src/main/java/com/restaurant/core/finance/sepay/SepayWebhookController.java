package com.restaurant.core.finance.sepay;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;

/**
 * Endpoint nhận webhook SePay. Không dùng JWT (SecurityConfig permitAll) — xác thực bằng API key trong service.
 * Thân phản hồi {"success": true, ...} với HTTP 200 là điều kiện SePay coi là đã nhận. Owner: Phạm Duy An.
 */
@RestController
@RequestMapping("/api/webhooks")
public class SepayWebhookController {

    private final SepayWebhookService service;

    public SepayWebhookController(SepayWebhookService service) {
        this.service = service;
    }

    @PostMapping(path = "/sepay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SepayWebhookService.WebhookResult> receive(
            @RequestHeader(name = "Authorization", required = false) String authorization, @RequestBody String rawBody) {
        return ApiResponse.ok(service.handle(authorization, rawBody));
    }
}
