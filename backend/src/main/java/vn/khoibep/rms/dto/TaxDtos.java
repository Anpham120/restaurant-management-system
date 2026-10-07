package vn.khoibep.rms.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** VAT by tax category and day (FR-20.1, BR-45). Rates in percent. */
public final class TaxDtos {

    private TaxDtos() {
    }

    /** @param rate in force from today */
    public record TaxCategoryRequest(@NotBlank @Size(max = 50) String name, @NotNull Integer rate) {
    }

    public record TaxRateRequest(@NotNull Integer rate) {
    }

    public record TaxRateDto(int rate, LocalDate effectiveFrom) {
    }

    /** @param rates from the first one; the last ones may start after today */
    public record TaxCategoryDto(Long id, String name, int currentRate, List<TaxRateDto> rates) {
    }
}
