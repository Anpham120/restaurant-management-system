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
import vn.khoibep.rms.report.dto.ReportDtos.ActionTotal;
import vn.khoibep.rms.report.dto.ReportDtos.DayRevenue;
import vn.khoibep.rms.report.dto.ReportDtos.DishProfit;
import vn.khoibep.rms.report.dto.ReportDtos.ExceptionsDto;
import vn.khoibep.rms.report.dto.ReportDtos.GrossProfitDto;
import vn.khoibep.rms.report.dto.ReportDtos.MethodRevenue;
import vn.khoibep.rms.report.dto.ReportDtos.PersonTotal;
import vn.khoibep.rms.report.dto.ReportDtos.SummaryDto;
import vn.khoibep.rms.report.dto.ReportDtos.TopItem;
import vn.khoibep.rms.report.enums.RevenueMethod;

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

    /** BR-21, BR-43: orders covered on those days; a split bill is covered by its last payment. */
    private static final String CLOSED_IN_RANGE = """
            o.status = 'PAID'
            and (o.closed_at at time zone :tz)::date between :from and :to""";

    /**
     * BR-21, BR-42: what came in on those days: each payment, and each deposit taken off a bill covered then.
     * Only payments count orders, each order once however many parts it was paid in (BR-43).
     */
    private static final String MONEY_IN = """
            select p.method, p.amount, p.paid_at as at, p.order_id, true as payment
            from payment p
            where %s
            union all
            select 'DEPOSIT', r.deposit_applied, o.closed_at, o.id, false
            from reservation r
            join orders o on o.reservation_id = r.id
            where %s and r.deposit_applied > 0""".formatted(PAID_IN_RANGE, CLOSED_IN_RANGE);

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public SummaryDto summary(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = range(from, to);

        List<MethodRevenue> byMethod = jdbc.query("""
                select m.method, sum(m.amount) as amount, count(*) as cnt
                from (%s) m
                group by m.method
                order by m.method""".formatted(MONEY_IN), params,
                (rs, i) -> new MethodRevenue(RevenueMethod.valueOf(rs.getString("method")),
                        rs.getLong("amount"), rs.getLong("cnt")));

        List<DayRevenue> byDay = jdbc.query("""
                select (m.at at time zone :tz)::date as day, sum(m.amount) as amount,
                       count(distinct m.order_id) filter (where m.payment) as cnt
                from (%s) m
                group by day
                order by day""".formatted(MONEY_IN), params,
                (rs, i) -> new DayRevenue(rs.getObject("day", LocalDate.class), rs.getLong("amount"),
                        rs.getLong("cnt")));

        List<TopItem> topItems = jdbc.query("""
                select oi.item_name, sum(oi.quantity) as qty, sum(oi.quantity * oi.unit_price) as amount
                from order_item oi
                join orders o on o.id = oi.order_id
                where %s
                  and oi.status not in ('CANCELLED', 'PENDING')
                group by oi.item_name
                order by qty desc, amount desc
                limit 10""".formatted(CLOSED_IN_RANGE), params,
                (rs, i) -> new TopItem(rs.getString("item_name"), rs.getLong("qty"), rs.getLong("amount")));

        long revenue = byMethod.stream().mapToLong(MethodRevenue::amount).sum();
        Long orders = jdbc.queryForObject("select count(distinct m.order_id) from (" + MONEY_IN + ") m where m.payment",
                params, Long.class);
        long orderCount = orders == null ? 0 : orders;
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
                join orders o on o.id = oi.order_id
                left join v_order_item_cost c on c.order_item_id = oi.id
                where %s
                  and oi.status not in ('CANCELLED', 'PENDING')
                group by oi.item_name
                order by revenue desc, oi.item_name""".formatted(CLOSED_IN_RANGE), params,
                (rs, i) -> DishProfit.of(rs.getString("item_name"), rs.getLong("qty"), rs.getLong("revenue"),
                        rs.getBoolean("complete") ? rs.getLong("cost") : null));
        Long revenue = jdbc.queryForObject("select coalesce(sum(m.amount), 0) from (" + MONEY_IN + ") m",
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
