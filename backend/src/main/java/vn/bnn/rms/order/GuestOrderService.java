package vn.bnn.rms.order;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.ApiException;
import vn.bnn.rms.common.RealtimeEvent.Alert;
import vn.bnn.rms.common.RealtimeEvents;
import vn.bnn.rms.order.OrderDtos.AddItemsRequest;
import vn.bnn.rms.order.OrderDtos.GuestItemDto;
import vn.bnn.rms.order.OrderDtos.GuestOrderDto;
import vn.bnn.rms.order.OrderDtos.GuestTableDto;
import vn.bnn.rms.payment.PaymentDtos.PaymentInstruction;
import vn.bnn.rms.payment.PaymentService;
import vn.bnn.rms.settings.SettingsService;
import vn.bnn.rms.table.DiningTable;
import vn.bnn.rms.table.DiningTableRepository;

/** The guest side of a table QR code (FR-06, FR-08.5). Everything is scoped to the table of the token (BR-11). */
@Service
@RequiredArgsConstructor
public class GuestOrderService {

    private final DiningTableRepository tables;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentService payments;
    private final SettingsService settings;
    private final ServiceRequestRepository requests;
    private final RealtimeEvents realtime;

    @Transactional(readOnly = true)
    public GuestTableDto view(String token) {
        DiningTable table = table(token);
        Order open = orders.findByTableIdAndStatus(table.getId(), OrderStatus.OPEN).orElse(null);
        return toDto(table, open);
    }

    /** BR-10: guest dishes wait for staff confirmation; the first submission opens the table's order. */
    @Transactional
    public GuestTableDto addItems(String token, AddItemsRequest request) {
        DiningTable table = table(token);
        orders.insertOpenOrderIfAbsent(table.getId());
        Order order = orders.findByTableIdAndStatusForUpdate(table.getId(), OrderStatus.OPEN).orElseThrow();
        orderService.buildItems(request.items(), ItemSource.GUEST).forEach(order::addItem);
        payments.cancelPendingTransfers(order.getId());
        orders.flush();
        realtime.orderChanged(order.getId(), table.getId(), table.getQrToken(), Alert.GUEST_DISHES);
        return toDto(table, order);
    }

    @Transactional
    public PaymentInstruction requestPayment(String token) {
        DiningTable table = table(token);
        Order order = orders.findByTableIdAndStatus(table.getId(), OrderStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("Bàn chưa có món để thanh toán"));
        return payments.requestTransfer(order.getId());
    }

    /** FR-06.6, BR-29: waiters' screens ring only for a new call, and the bill needs an order. */
    @Transactional
    public GuestTableDto call(String token, ServiceRequestType type) {
        DiningTable table = table(token);
        Order open = orders.findByTableIdAndStatus(table.getId(), OrderStatus.OPEN).orElse(null);
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
        long total = order.total();
        boolean canPay = pending == 0 && total > 0;
        return new GuestTableDto(table.getName(), restaurantName, new GuestOrderDto(order.getId(),
                order.getItems().stream().map(GuestItemDto::from).toList(), total, pending, canPay), openRequests);
    }

    private DiningTable table(String token) {
        return tables.findByQrToken(token)
                .orElseThrow(() -> ApiException.notFound("Mã QR không còn hiệu lực, vui lòng gọi nhân viên"));
    }
}
