package vn.bnn.rms.report.dto;

import java.time.LocalDate;
import java.util.List;

import vn.bnn.rms.payment.enums.PaymentMethod;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record SummaryDto(LocalDate from, LocalDate to, long revenue, long orderCount, long averagePerOrder,
                             List<MethodRevenue> byMethod, List<DayRevenue> byDay, List<TopItem> topItems) {
    }

    public record MethodRevenue(PaymentMethod method, long amount, long count) {
    }

    public record DayRevenue(LocalDate date, long amount, long count) {
    }

    public record TopItem(String itemName, long quantity, long amount) {
    }
}
