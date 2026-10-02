package vn.khoibep.rms.payment.service;

import java.time.Clock;
import java.time.Instant;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import vn.khoibep.rms.common.realtime.RealtimeEvent;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.payment.dto.PaymentDtos.WebhookStatus;

/**
 * BR-32: counts SePay deliveries that fail in a row. The third one warns the cashier screens once, so the cashier
 * checks the bank app and confirms by hand; the next delivery that works clears the warning. Kept in memory like the
 * rate limits, so a restart counts from zero again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SepayWebhookMonitor {

    /** SePay resends a failed delivery after 1 and 2 minutes, so a broken webhook shows within about 2 minutes. */
    public static final int FAILURES_BEFORE_ALERT = 3;

    /** NFR-12: every failed delivery, for the monitoring server. */
    static final String FAILED_METRIC = "rms.sepay.webhook.failed";

    private final RealtimeEvents realtime;
    private final Clock clock;
    private final MeterRegistry meters;

    private int failures;
    private Instant since;
    private String lastError;

    /** NFR-12: registered at 0 on start, so the first failure after a restart still counts as an increase. */
    @PostConstruct
    void registerMetrics() {
        Gauge.builder("rms.sepay.webhook.consecutive.failures", this, monitor -> monitor.status().failures())
                .description("SePay deliveries that failed in a row (BR-32)")
                .register(meters);
        meters.counter(FAILED_METRIC);
    }

    public synchronized void succeeded() {
        if (failures >= FAILURES_BEFORE_ALERT) {
            log.info("SePay webhook works again after {} failures in a row", failures);
            realtime.staffNotice(RealtimeEvent.WEBHOOK_STATUS);
        }
        failures = 0;
        since = null;
        lastError = null;
    }

    public synchronized void failed(String reason) {
        if (failures == 0) {
            since = clock.instant();
        }
        failures++;
        lastError = reason;
        meters.counter(FAILED_METRIC).increment();
        if (failures == FAILURES_BEFORE_ALERT) {
            log.error("SePay webhook failed {} times in a row since {}, last: {}. Transfers are not confirmed "
                    + "automatically until it works again", failures, since, reason);
            realtime.staffNotice(RealtimeEvent.WEBHOOK_STATUS);
        } else {
            log.warn("SePay webhook failed ({} in a row): {}", failures, reason);
        }
    }

    public synchronized WebhookStatus status() {
        return new WebhookStatus(failures >= FAILURES_BEFORE_ALERT, failures, since, lastError);
    }
}
