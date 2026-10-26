package com.restaurant.core.common;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.restaurant.core.common.exception.PostgresErrorTranslator;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresErrorTranslatorTest {

    private final PostgresErrorTranslator translator = new PostgresErrorTranslator();

    private PostgresErrorTranslator.Translated of(String state, String message) {
        return translator.translate(new DataIntegrityViolationException("wrap", new SQLException(message, state)));
    }

    @Test
    void businessCodesKeepDatabaseMessage() {
        PostgresErrorTranslator.Translated t = of("RC403", "ERROR: nhân viên 5 không có vai trò MANAGER đang hoạt động\n  Where: PL/pgSQL function");
        assertThat(t.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(t.code()).isEqualTo("RC403");
        assertThat(t.message()).isEqualTo("nhân viên 5 không có vai trò MANAGER đang hoạt động");
        assertThat(of("RC001", "ERROR: Thiếu nguyên liệu").status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(of("RC409", "ERROR: x").status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(of("RC422", "ERROR: x").status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void internalCodesHideDatabaseMessage() {
        PostgresErrorTranslator.Translated t = of("RC500", "ERROR: RAW 7: số dư 3 ≠ tổng sổ kho 4");
        assertThat(t.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(t.message()).doesNotContain("RAW 7");
        assertThat(of("40P01", "deadlock").status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(of("23P01", "conflicting key value violates exclusion constraint").code()).isEqualTo("OVERLAP");
        assertThat(of("42501", "permission denied for table invoices").code()).isEqualTo("DB_PERMISSION");
        assertThat(of("XX000", "boom")).isNull();
    }
}
