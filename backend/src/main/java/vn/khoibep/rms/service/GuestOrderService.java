package vn.khoibep.rms.service;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent.Alert;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.common.security.RateLimiter;
import vn.khoibep.rms.dto.OrderDtos.AddItemsRequest;
import vn.khoibep.rms.dto.OrderDtos.GuestItemDto;
import vn.khoibep.rms.dto.OrderDtos.GuestOrderDto;
import vn.khoibep.rms.dto.OrderDtos.GuestTableDto;
import vn.khoibep.rms.dto.PaymentDtos.PaymentInstruction;
import vn.khoibep.rms.entity.DiningTable;
import vn.khoibep.rms.entity.Order;
import vn.khoibep.rms.enums.ItemSource;
import vn.khoibep.rms.enums.ItemStatus;
import vn.khoibep.rms.enums.OrderStatus;
import vn.khoibep.rms.enums.ServiceRequestType;
import vn.khoibep.rms.repository.DiningTableRepository;
import vn.khoibep.rms.repository.OrderRepository;
import vn.khoibep.rms.repository.ServiceRequestRepository;

/** The guest side of a table QR code (FR-06, FR-08.5). Everything is scoped to the table of the token (BR-11). */
@Service
@RequiredArgsConstructor
public class GuestOrderService {

    /** BR-30: requests a table's QR page may send each minute, and dishes it may keep waiting for confirmation. */
    static final int TABLE_REQUESTS_PER_MINUTE = 10;
    static final int MAX_PENDING_DISHES = 30;

    private final DiningTableRepository tables;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentService payments;
    private final SettingsService settings;
    private final ServiceRequestRepository requests;
    private final RateLimiter rateLimiter;
    private final RealtimeEvents realtime;

    @Transactional(readOnly = true)
    public GuestTableDto view(String token) {
        DiningTable table = table(token);
        Order open = orders.findOpenByTableId(table.getId()).orElse(null);
        return toDto(table, open);
    }

    /**
     * BR-10: guest dishes wait for staff confirmation; the first submission opens the table's order.
     * BR-30: at most {@value #MAX_PENDING_DISHES} dishes wait at a time.
     */
    @Transactional
    public GuestTableDto addItems(String token, AddItemsRequest request) {
        DiningTable table = tableSending(token);
        Order order = openOrderForUpdate(table);
        if (order.countItems(ItemStatus.PENDING) + request.items().size() > MAX_PENDING_DISHES) {
            throw ApiException.conflict(
                    "Bàn đang có nhiều món chờ nhân viên xác nhận. Vui lòng chờ nhân viên xác nhận rồi gọi thêm");
        }
        orderService.buildItems(request.items(), ItemSource.GUEST).forEach(order::addItem);
        payments.cancelPendingTransfers(order.getId());
        orders.flush();
        realtime.orderChanged(order.getId(), order.tableId(), order.guestTokens(), Alert.GUEST_DISHES);
        return toDto(table, order);
    }

    @Transactional
    public PaymentInstruction requestPayment(String token) {
        DiningTable table = tableSending(token);
        Long orderId = orders.findOpenOrderIdByTableId(table.getId())
                .orElseThrow(() -> ApiException.conflict("Bàn chưa có món để thanh toán"));
        return payments.requestTransfer(orderId);
    }

    /**
     * BR-04: the open order holding the table, locked, or a new one opened at it. A guest at a table put together
     * with another orders into their shared order (BR-36). Two phones sending at once still get one order: the
     * insert gives way on ux_orders_open_table, and the order is locked and read fresh before it changes.
     */
    private Order openOrderForUpdate(DiningTable table) {
        Long heldBy = orders.findOpenOrderIdByTableId(table.getId()).orElse(null);
        if (heldBy != null) {
            Order order = orders.findByIdForUpdate(heldBy).orElseThrow();
            // The order may have closed or left the table between the two reads.
            if (order.isOpen() && order.activeTables().stream().anyMatch(t -> t.getId().equals(table.getId()))) {
                return order;
            }
        }
        orders.insertOpenOrderIfAbsent(table.getId());
        Order order = orders.findByTableIdAndStatusForUpdate(table.getId(), OrderStatus.OPEN).orElseThrow();
        if (order.activeTables().isEmpty()) {
            order.hold(table);
        }
        return order;
    }

    /** FR-06.6, BR-29: waiters' screens ring only for a new call, and the bill needs an order. */
    @Transactional
    public GuestTableDto call(String token, ServiceRequestType type) {
        DiningTable table = tableSending(token);
        Order open = orders.findOpenByTableId(table.getId()).orElse(null);
        if (type == ServiceRequestType.BILL && open == null) {
            throw ApiException.conflict("Bàn chưa gọi món nên chưa tính tiền được");
        }
        if (requests.insertOpenIfAbsent(table.getId(), type.name(), Instant.now()) > 0) {
            realtime.requestsChanged(table.getId(), table.getQrToken(), Alert.SERVICE_REQUEST);
        }
        return toDto(table, open);
    }

    private GuestTableDto toDto(DiningTable table, Order order) {
        String restaurantName = settings.current().getName();
        List<ServiceRequestType> openRequests = requests.findOpenTypes(table.getId());
        if (order == null) {
            return new GuestTableDto(table.getName(), restaurantName, null, openRequests);
        }
        int pending = order.countItems(ItemStatus.PENDING);
        long due = order.due();
        // BR-13: not while dishes or a discount still wait for staff; the guest pays what is left (BR-43).
        boolean canPay = pending == 0 && order.countPendingAdjustments() == 0 && due > 0;
        return new GuestTableDto(table.getName(), restaurantName, new GuestOrderDto(order.getId(),
                order.getItems().stream().map(GuestItemDto::from).toList(), order.discountTotal(),
                order.depositCredit(), order.total(), order.paidAmount(), due, pending, canPay), openRequests);
    }

    private DiningTable table(String token) {
        return tables.findByQrToken(token)
                .orElseThrow(() -> ApiException.notFound("Mã QR không còn hiệu lực, vui lòng gọi nhân viên"));
    }

    /** The table of the token, once one of its requests for this minute is used (BR-30). Unknown tokens use none. */
    private DiningTable tableSending(String token) {
        DiningTable table = table(token);
        rateLimiter.acquire("table:" + table.getId(), TABLE_REQUESTS_PER_MINUTE,
                "Bàn này gửi quá nhiều lần, vui lòng thử lại sau %d giây");
        return table;
    }
}
