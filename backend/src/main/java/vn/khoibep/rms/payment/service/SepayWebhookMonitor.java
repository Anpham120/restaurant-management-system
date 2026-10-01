package vn.khoibep.rms.payment.service;

import java.time.Clock;
import java.time.Instant;

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

    private final RealtimeEvents realtime;
    private final Clock clock;

    private int failures;
    private Instant since;
    private String lastError;

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
