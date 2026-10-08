package vn.khoibep.rms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.service.TaxService.TaxRates;

/** BR-45. */
class TaxRatesTest {

    private static final LocalDate CUT = LocalDate.of(2026, 7, 1);

    private final TaxRates rates = new TaxRates(Map.of(1L, new TreeMap<>(Map.of(LocalDate.of(2026, 1, 1), 10, CUT, 8))));

    @Test
    void rateInForceOnTheDay() {
        assertThat(rates.on(1L, CUT.minusDays(1))).isEqualTo(10);
        assertThat(rates.on(1L, CUT)).isEqualTo(8);
    }

    @Test
    void beforeTheFirstRateTheFirstOneApplies() {
        assertThat(rates.on(1L, LocalDate.of(2025, 12, 31))).isEqualTo(10);
    }

    @Test
    void categoryWithoutRateIsAConflictNotAServerError() {
        assertThatThrownBy(() -> rates.on(7L, CUT))
                .isInstanceOf(ApiException.class)
                .hasMessage("Loại thuế 7 chưa có thuế suất")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }
}
