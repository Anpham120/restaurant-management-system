package com.restaurant.core.finance.sepay;

import java.math.BigDecimal;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.common.exception.ApiException;

/**
 * Gọi rc_process_verified_qr bằng login thuộc role rc_webhook — role rc_app của ứng dụng KHÔNG có quyền hàm này
 * (docs/09-acl-v4.1.sql). Pool riêng nhỏ, tạo khi webhook đầu tiên đến; không phải bean DataSource nên không ảnh hưởng
 * DataSource chính của Spring Boot.
 */
@Repository
public class SepayWebhookRepository implements DisposableBean {

    private final SepayProperties props;
    private volatile JdbcClient jdbc;
    private HikariDataSource dataSource;

    public SepayWebhookRepository(SepayProperties props) {
        this.props = props;
    }

    /** rc_process_verified_qr(p_transaction_code, p_reference, p_amount, p_payload) → PROCESSED | MISMATCH | AFTER_CANCEL. Idempotent theo mã giao dịch. */
    public String processVerifiedQr(String transactionCode, String reference, BigDecimal amount, String rawPayload) {
        return client().sql("SELECT rc_process_verified_qr(:code, :ref, :amount, :payload)")
                .param("code", transactionCode).param("ref", reference).param("amount", amount).param("payload", rawPayload)
                .query(String.class).single();
    }

    private JdbcClient client() {
        JdbcClient c = jdbc;
        if (c == null) {
            synchronized (this) {
                if (jdbc == null) {
                    SepayProperties.WebhookDb db = props.webhookDb();
                    if (db == null || !SepayProperties.hasText(db.url()) || !SepayProperties.hasText(db.username())
                            || !SepayProperties.hasText(db.password())) {
                        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED",
                                "Chưa cấu hình kết nối database cho webhook (DB_WEBHOOK_USER/DB_WEBHOOK_PASSWORD)");
                    }
                    dataSource = new HikariDataSource();
                    dataSource.setJdbcUrl(db.url());
                    dataSource.setUsername(db.username());
                    dataSource.setPassword(db.password());
                    dataSource.setMaximumPoolSize(3);
                    dataSource.setPoolName("sepay-webhook");
                    jdbc = JdbcClient.create(dataSource);
                }
                c = jdbc;
            }
        }
        return c;
    }

    @Override
    public synchronized void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
