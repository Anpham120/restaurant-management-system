package vn.khoibep.rms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.khoibep.rms.model.EInvoiceLine;

/** BR-46: the tax taken back out of prices that include it, and a discount spread over the tax rates. */
class EInvoiceMathTest {

    @Test
    void theTaxIsTakenOutOfAPriceThatIncludesIt() {
        assertThat(EInvoiceLine.beforeTax(329_000, 8)).isEqualTo(304_630);
        assertThat(EInvoiceLine.beforeTax(44_000, 10)).isEqualTo(40_000);
        assertThat(EInvoiceLine.beforeTax(108_000, 10)).isEqualTo(98_182);
        assertThat(EInvoiceLine.beforeTax(1, 8)).isEqualTo(1);
        assertThat(EInvoiceLine.beforeTax(5_000, 0)).isEqualTo(5_000);
    }

    @Test
    void aDiscountGoesToTheRatesInProportionAndAddsUp() {
        assertThat(EInvoiceService.spread(32_900, Map.of(8, 329_000L, 10, 0L))).containsEntry(8, 32_900L)
                .containsEntry(10, 0L);
        assertThat(EInvoiceService.spread(10_000, Map.of(8, 300_000L, 10, 100_000L))).containsEntry(8, 7_500L)
                .containsEntry(10, 2_500L);
        // 332,667 + 332,667 + 333,666: the two đồng left go to the remainders of 667.
        assertThat(EInvoiceService.spread(999, Map.of(5, 333L, 8, 333L, 10, 334L))).containsEntry(5, 333L)
                .containsEntry(8, 333L).containsEntry(10, 333L);
    }

    @Test
    void theDongLeftGoToTheLargestRemaindersTheHigherRateFirst() {
        assertThat(EInvoiceService.spread(10, Map.of(5, 1L, 8, 1L, 10, 1L))).containsEntry(5, 3L)
                .containsEntry(8, 3L).containsEntry(10, 4L);
        assertThat(EInvoiceService.spread(0, Map.of(8, 100L))).isEmpty();
    }
}
