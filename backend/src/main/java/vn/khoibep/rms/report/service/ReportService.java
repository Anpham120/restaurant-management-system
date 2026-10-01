package vn.khoibep.rms.report.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.audit.enums.AuditAction;
import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.config.AppProperties;
import vn.khoibep.rms.payment.enums.PaymentMethod;
import vn.khoibep.rms.report.dto.ReportDtos.ActionTotal;
import vn.khoibep.rms.report.dto.ReportDtos.DayRevenue;
import vn.khoibep.rms.report.dto.ReportDtos.DishProfit;
import vn.khoibep.rms.report.dto.ReportDtos.ExceptionsDto;
import vn.khoibep.rms.report.dto.ReportDtos.GrossProfitDto;
import vn.khoibep.rms.report.dto.ReportDtos.MethodRevenue;
import vn.khoibep.rms.report.dto.ReportDtos.PersonTotal;
import vn.khoibep.rms.report.dto.ReportDtos.SummaryDto;
import vn.khoibep.rms.report.dto.ReportDtos.TopItem;

/** BR-21: revenue = confirmed payments, grouped by the Vietnam-time date they were confirmed. */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** What the exceptions report counts; a price change is no exception and stays in the audit log. */
    private static final List<AuditAction> EXCEPTIONS =
            List.of(AuditAction.ITEM_CANCELLED, AuditAction.DISCOUNT_GIVEN, AuditAction.MANUAL_CONFIRMATION);

    private static final String PAID_IN_RANGE = """
            p.status = 'PAID'
            and (p.paid_at at time zone :tz)::date between :from and :to""";

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public SummaryDto summary(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = range(from, to);

        List<MethodRevenue> byMethod = jdbc.query("""
                select p.method, sum(p.amount) as amount, count(*) as cnt
                from payment p
                where %s
                group by p.method
                order by p.method""".formatted(PAID_IN_RANGE), params,
                (rs, i) -> new MethodRevenue(PaymentMethod.valueOf(rs.getString("method")),
                        rs.getLong("amount"), rs.getLong("cnt")));

        List<DayRevenue> byDay = jdbc.query("""
                select (p.paid_at at time zone :tz)::date as day, sum(p.amount) as amount, count(*) as cnt
                from payment p
                where %s
                group by day
                order by day""".formatted(PAID_IN_RANGE), params,
                (rs, i) -> new DayRevenue(rs.getObject("day", LocalDate.class), rs.getLong("amount"),
                        rs.getLong("cnt")));

        List<TopItem> topItems = jdbc.query("""
                select oi.item_name, sum(oi.quantity) as qty, sum(oi.quantity * oi.unit_price) as amount
                from order_item oi
                join payment p on p.order_id = oi.order_id
                where %s
                  and oi.status not in ('CANCELLED', 'PENDING')
                group by oi.item_name
                order by qty desc, amount desc
                limit 10""".formatted(PAID_IN_RANGE), params,
                (rs, i) -> new TopItem(rs.getString("item_name"), rs.getLong("qty"), rs.getLong("amount")));

        long revenue = byMethod.stream().mapToLong(MethodRevenue::amount).sum();
        long orderCount = byMethod.stream().mapToLong(MethodRevenue::count).sum();
        return new SummaryDto(from, to, revenue, orderCount, orderCount == 0 ? 0 : revenue / orderCount,
                byMethod, byDay, topItems);
    }

    /** FR-10.4, BR-40: dishes by name; a dish has a cost only when every portion sold had one. */
    @Transactional(readOnly = true)
    public GrossProfitDto grossProfit(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = range(from, to);
        List<DishProfit> dishes = jdbc.query("""
                select oi.item_name, sum(oi.quantity) as qty, sum(oi.quantity * oi.unit_price) as revenue,
                       sum(c.cost) as cost, bool_and(coalesce(c.cost_complete, false)) as complete
                from order_item oi
                join payment p on p.order_id = oi.order_id
                left join v_order_item_cost c on c.order_item_id = oi.id
                where %s
                  and oi.status not in ('CANCELLED', 'PENDING')
                group by oi.item_name
                order by revenue desc, oi.item_name""".formatted(PAID_IN_RANGE), params,
                (rs, i) -> DishProfit.of(rs.getString("item_name"), rs.getLong("qty"), rs.getLong("revenue"),
                        rs.getBoolean("complete") ? rs.getLong("cost") : null));
        Long revenue = jdbc.queryForObject("select coalesce(sum(p.amount), 0) from payment p where " + PAID_IN_RANGE,
                params, Long.class);
        return GrossProfitDto.of(from, to, revenue == null ? 0 : revenue, dishes);
    }

    /** FR-10.5, BR-40: cancelled dishes, discounts and dishes given free, transfers confirmed by hand (BR-34). */
    @Transactional(readOnly = true)
    public ExceptionsDto exceptions(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = range(from, to)
                .addValue("actions", EXCEPTIONS.stream().map(AuditAction::name).toList());
        String inRange = """
                a.action in (:actions)
                and (a.created_at at time zone :tz)::date between :from and :to""";
        List<ActionTotal> byAction = jdbc.query("""
                select a.action, count(*) as cnt, coalesce(sum(a.amount), 0) as amount
                from audit_entry a
                where %s
                group by a.action
                order by a.action""".formatted(inRange), params,
                (rs, i) -> new ActionTotal(AuditAction.valueOf(rs.getString("action")), rs.getLong("cnt"),
                        rs.getLong("amount")));
        List<PersonTotal> byPerson = jdbc.query("""
                select e.id, e.full_name, a.action, count(*) as cnt, coalesce(sum(a.amount), 0) as amount
                from audit_entry a
                join employee e on e.id = a.employee_id
                where %s
                group by e.id, e.full_name, a.action
                order by e.full_name, e.id, a.action""".formatted(inRange), params,
                (rs, i) -> new PersonTotal(rs.getLong("id"), rs.getString("full_name"),
                        AuditAction.valueOf(rs.getString("action")), rs.getLong("cnt"), rs.getLong("amount")));
        return new ExceptionsDto(from, to, byAction, byPerson);
    }

    /** Vietnam dates, at most a year at a time. */
    private MapSqlParameterSource range(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw ApiException.badRequest("Chỉ xem được tối đa 1 năm mỗi lần");
        }
        return new MapSqlParameterSource()
                .addValue("tz", props.timezone())
                .addValue("from", from)
                .addValue("to", to);
    }
}
