package vn.khoibep.rms.report.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.config.AppProperties;
import vn.khoibep.rms.payment.enums.PaymentMethod;
import vn.khoibep.rms.report.dto.ReportDtos.DayRevenue;
import vn.khoibep.rms.report.dto.ReportDtos.MethodRevenue;
import vn.khoibep.rms.report.dto.ReportDtos.SummaryDto;
import vn.khoibep.rms.report.dto.ReportDtos.TopItem;

/** BR-21: revenue = confirmed payments, grouped by the Vietnam-time date they were confirmed. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String PAID_IN_RANGE = """
            p.status = 'PAID'
            and (p.paid_at at time zone :tz)::date between :from and :to""";

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public SummaryDto summary(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw ApiException.badRequest("Chỉ xem được tối đa 1 năm mỗi lần");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tz", props.timezone())
                .addValue("from", from)
                .addValue("to", to);

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
}
