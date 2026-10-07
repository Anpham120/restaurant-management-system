package vn.khoibep.rms.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.model.CashExpense;
import vn.khoibep.rms.model.CashShift;

/** Shifts of the cash drawer (FR-17). Amounts in VND. */
public final class CashShiftDtos {

    private CashShiftDtos() {
    }

    public record OpenShiftRequest(@NotNull @Min(0) @Max(1_000_000_000) Long openingFloat) {
    }

    public record ExpenseRequest(@NotNull @Min(1) @Max(1_000_000_000) Long amount,
                                 @NotBlank @Size(max = 300) String reason) {
    }

    /** @param note required when the count differs from what was expected (BR-39) */
    public record CloseShiftRequest(@NotNull @Min(0) @Max(10_000_000_000L) Long countedCash,
                                    @Size(max = 300) String note) {
    }

    public record CashExpenseDto(Long id, long amount, String reason, String createdByName, Instant createdAt) {
        public static CashExpenseDto from(CashExpense e) {
            return new CashExpenseDto(e.getId(), e.getAmount(), e.getReason(), e.getCreatedBy().getFullName(),
                    e.getCreatedAt());
        }
    }

    /**
     * @param expectedCash opening float + cash taken − cash paid out; worked out while open, as recorded once closed
     * @param difference   counted − expected; null while the shift is open
     */
    public record CashShiftDto(Long id, String openedByName, Instant openedAt, long openingFloat, long cashTaken,
                               long cashPayments, long expenseTotal, long expectedCash, String closedByName,
                               Instant closedAt, Long countedCash, Long difference, String closeNote,
                               List<CashExpenseDto> expenses) {
        public static CashShiftDto from(CashShift s, long cashTaken, long cashPayments, List<CashExpense> expenses) {
            long expenseTotal = expenses.stream().mapToLong(CashExpense::getAmount).sum();
            long expected = s.isOpen() ? s.getOpeningFloat() + cashTaken - expenseTotal : s.getExpectedCash();
            return new CashShiftDto(s.getId(), s.getOpenedBy().getFullName(), s.getOpenedAt(), s.getOpeningFloat(),
                    cashTaken, cashPayments, expenseTotal, expected,
                    s.getClosedBy() == null ? null : s.getClosedBy().getFullName(), s.getClosedAt(),
                    s.getCountedCash(), s.isOpen() ? null : s.getCountedCash() - s.getExpectedCash(),
                    s.getCloseNote(), expenses.stream().map(CashExpenseDto::from).toList());
        }
    }
}
