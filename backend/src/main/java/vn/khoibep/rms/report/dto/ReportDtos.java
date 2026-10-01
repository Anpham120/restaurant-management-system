package vn.khoibep.rms.report.dto;

import java.time.LocalDate;
import java.util.List;

import vn.khoibep.rms.audit.enums.AuditAction;
import vn.khoibep.rms.report.enums.RevenueMethod;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record SummaryDto(LocalDate from, LocalDate to, long revenue, long orderCount, long averagePerOrder,
                             List<MethodRevenue> byMethod, List<DayRevenue> byDay, List<TopItem> topItems) {
    }

    /** @param count payments, or deposits taken off bills */
    public record MethodRevenue(RevenueMethod method, long amount, long count) {
    }

    public record DayRevenue(LocalDate date, long amount, long count) {
    }

    public record TopItem(String itemName, long quantity, long amount) {
    }

    /** BR-40: cost and gross profit are null when some portion sold had no cost. */
    public record DishProfit(String itemName, long quantity, long revenue, Long cost, Long grossProfit) {
        public static DishProfit of(String itemName, long quantity, long revenue, Long cost) {
            return new DishProfit(itemName, quantity, revenue, cost, cost == null ? null : revenue - cost);
        }
    }

    /**
     * BR-40, in VND.
     *
     * @param dishRevenue    dishes at the price they were ordered, before discounts on the bill
     * @param revenue        what was paid (BR-21)
     * @param discounts      discounts and dishes given free: dish revenue − revenue
     * @param costedRevenue  dish revenue of the dishes with a full cost; cost and gross profit are about these
     */
    public record GrossProfitDto(LocalDate from, LocalDate to, long dishRevenue, long revenue, long discounts,
                                 long costedRevenue, long cost, long grossProfit, List<DishProfit> dishes) {
        public static GrossProfitDto of(LocalDate from, LocalDate to, long revenue, List<DishProfit> dishes) {
            long dishRevenue = dishes.stream().mapToLong(DishProfit::revenue).sum();
            List<DishProfit> costed = dishes.stream().filter(d -> d.cost() != null).toList();
            long costedRevenue = costed.stream().mapToLong(DishProfit::revenue).sum();
            long cost = costed.stream().mapToLong(DishProfit::cost).sum();
            return new GrossProfitDto(from, to, dishRevenue, revenue, dishRevenue - revenue, costedRevenue, cost,
                    costedRevenue - cost, dishes);
        }
    }

    public record ActionTotal(AuditAction action, long count, long amount) {
    }

    public record PersonTotal(Long employeeId, String employeeName, AuditAction action, long count, long amount) {
    }

    /** FR-10.5: counts and amounts by kind of action and by who did it. */
    public record ExceptionsDto(LocalDate from, LocalDate to, List<ActionTotal> byAction, List<PersonTotal> byPerson) {
    }
}
