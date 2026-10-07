package vn.khoibep.rms.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.common.util.Phones;
import vn.khoibep.rms.dto.CustomerDtos.AttachRequest;
import vn.khoibep.rms.dto.CustomerDtos.BookingDto;
import vn.khoibep.rms.dto.CustomerDtos.ConsentRequest;
import vn.khoibep.rms.dto.CustomerDtos.CustomerDetailDto;
import vn.khoibep.rms.dto.CustomerDtos.CustomerDto;
import vn.khoibep.rms.dto.CustomerDtos.CustomerRequest;
import vn.khoibep.rms.dto.CustomerDtos.VisitDto;
import vn.khoibep.rms.entity.Customer;
import vn.khoibep.rms.entity.Order;
import vn.khoibep.rms.enums.OrderStatus;
import vn.khoibep.rms.repository.CustomerRepository;
import vn.khoibep.rms.repository.OrderRepository;
import vn.khoibep.rms.repository.ReservationRepository;

/** FR-19, BR-44: guests known by their phone number, their visits, and whether they may be sent messages. */
@Service
@RequiredArgsConstructor
public class CustomerService {

    /** The paid orders of each guest: how many, the latest, and what they brought in with their deposits. */
    private static final String VISITS = """
            select o.customer_id, count(*) as visits, max(o.closed_at) as last_visit,
                   coalesce(sum(p.paid), 0) + coalesce(sum(r.deposit_applied), 0) as spent
            from orders o
            left join (select order_id, sum(amount) as paid from payment where status = 'PAID' group by order_id) p
              on p.order_id = o.id
            left join reservation r on r.id = o.reservation_id
            where o.status = 'PAID' and o.customer_id in (:ids)
            group by o.customer_id""";

    /** Lists and histories show at most this many rows. */
    private static final int ROWS = 50;

    private record Visits(long count, long spent, Timestamp last) {
    }

    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final ReservationRepository reservations;
    private final NamedParameterJdbcTemplate jdbc;
    private final RealtimeEvents realtime;
    private final Clock clock;

    /** FR-19.1: by any part of the phone number, +84 in front read as 0, or of the name; the newest guests first. */
    @Transactional(readOnly = true)
    public List<CustomerDto> search(String query) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String digits = text.replaceAll("[^0-9]", "");
        if (text.startsWith("+84") || digits.length() == 11 && digits.startsWith("84")) {
            digits = "0" + digits.substring(2);
        }
        List<Customer> found = text.isEmpty() ? customers.findByOrderByCreatedAtDesc(PageRequest.of(0, ROWS))
                : customers.search(digits, text, PageRequest.of(0, ROWS));
        Map<Long, Visits> visits = visits(found.stream().map(Customer::getId).toList());
        return found.stream().map(c -> dto(c, visits.get(c.getId()))).toList();
    }

    /** FR-19.3: the guest with the latest paid visits and bookings. */
    @Transactional(readOnly = true)
    public CustomerDetailDto get(Long id) {
        Customer customer = customer(id);
        List<VisitDto> visits = orders.findByCustomerIdAndStatusOrderByClosedAtDesc(id, OrderStatus.PAID,
                        PageRequest.of(0, ROWS)).stream()
                .map(o -> new VisitDto(o.getId(), o.getClosedAt(), o.tableLabel(), o.paidAmount() + o.depositCredit()))
                .toList();
        List<BookingDto> bookings = reservations.findByCustomerIdOrderByReservedAtDesc(id, PageRequest.of(0, ROWS))
                .stream().map(r -> new BookingDto(r.getId(), r.getCode(), r.getReservedAt(), r.getGuestCount(),
                        r.getStatus()))
                .toList();
        return new CustomerDetailDto(dto(customer), visits, bookings);
    }

    @Transactional
    public CustomerDto update(Long id, CustomerRequest request) {
        Customer customer = customer(id);
        customer.describe(blankToNull(request.name()), blankToNull(request.note()));
        return dto(customer);
    }

    /** FR-19.2: the guest of this number joins the open order, made when new. */
    @Transactional
    public CustomerDto attach(Long orderId, AttachRequest request) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));
        if (!order.isOpen()) {
            throw ApiException.conflict("Đơn đã đóng");
        }
        Customer customer = findOrCreate(request.phone(), request.name());
        if (customer == null) {
            throw ApiException.badRequest("Số điện thoại phải có 10 số, bắt đầu bằng 0");
        }
        order.setCustomer(customer);
        realtime.orderChanged(order.getId(), order.tableId(), order.guestTokens());
        return dto(customer);
    }

    /** BR-44: the guest of this number, made when new; null when it is no Vietnamese phone number. */
    @Transactional
    public Customer findOrCreate(String rawPhone, String name) {
        String phone = Phones.normalize(rawPhone);
        if (phone == null) {
            return null;
        }
        String given = blankToNull(name);
        Customer customer = customers.findByPhone(phone)
                .orElseGet(() -> customers.save(new Customer(phone, given, clock.instant())));
        if (customer.getName() == null && given != null) {
            customer.describe(given, customer.getNote());
        }
        return customer;
    }

    /** FR-19.4: how and when the guest agreed to hear from the restaurant. */
    @Transactional
    public CustomerDto consent(Long id, ConsentRequest request) {
        Customer customer = customer(id);
        customer.consent(request.channel(), request.source().trim(), clock.instant());
        return dto(customer);
    }

    @Transactional
    public CustomerDto optOut(Long id) {
        Customer customer = customer(id);
        customer.optOut(clock.instant());
        return dto(customer);
    }

    private CustomerDto dto(Customer c) {
        return dto(c, visits(List.of(c.getId())).get(c.getId()));
    }

    private static CustomerDto dto(Customer c, Visits v) {
        return new CustomerDto(c.getId(), c.getPhone(), c.getName(), c.getNote(), c.getConsentChannel(),
                c.getConsentAt(), c.getConsentSource(), c.getOptedOutAt(), c.mayContact(), v == null ? 0 : v.count(),
                v == null ? 0 : v.spent(), v == null || v.last() == null ? null : v.last().toInstant());
    }

    private Map<Long, Visits> visits(List<Long> ids) {
        Map<Long, Visits> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query(VISITS, new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getLong("customer_id"),
                    new Visits(rs.getLong("visits"), rs.getLong("spent"), rs.getTimestamp("last_visit")));
        });
        return result;
    }

    private Customer customer(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
