package vn.bnn.rms.table;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.ApiException;
import vn.bnn.rms.common.RealtimeEvent;
import vn.bnn.rms.common.RealtimeEvents;
import vn.bnn.rms.config.AppProperties;
import vn.bnn.rms.order.ItemStatus;
import vn.bnn.rms.order.Order;
import vn.bnn.rms.order.OrderRepository;
import vn.bnn.rms.order.OrderStatus;
import vn.bnn.rms.table.TableDtos.TableDto;
import vn.bnn.rms.table.TableDtos.TableRequest;

@Service
@RequiredArgsConstructor
public class TableService {

    private final DiningTableRepository tables;
    private final OrderRepository orders;
    private final RealtimeEvents realtime;
    private final AppProperties props;

    /** Floor plan: state is derived from open orders, never stored (BR-04). */
    @Transactional(readOnly = true)
    public List<TableDto> list() {
        Map<Long, Order> openByTable = orders.findWithItemsByStatus(OrderStatus.OPEN).stream()
                .filter(o -> o.getTable() != null)
                .collect(Collectors.toMap(Order::tableId, Function.identity()));
        return tables.findAllByOrderByAreaAscNameAsc().stream()
                .map(t -> toDto(t, openByTable.get(t.getId())))
                .toList();
    }

    @Transactional
    public TableDto create(TableRequest request) {
        if (tables.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("Tên bàn đã tồn tại");
        }
        DiningTable table = new DiningTable();
        apply(table, request);
        table.setQrToken(QrTokenGenerator.newToken());
        tables.save(table);
        realtime.staffNotice(RealtimeEvent.TABLES_CHANGED);
        return toDto(table, null);
    }

    @Transactional
    public TableDto update(Long id, TableRequest request) {
        if (tables.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Tên bàn đã tồn tại");
        }
        DiningTable table = get(id);
        apply(table, request);
        realtime.staffNotice(RealtimeEvent.TABLES_CHANGED);
        return toDto(table, null);
    }

    /** BR-18: a table with order history is kept. */
    @Transactional
    public void delete(Long id) {
        if (orders.existsByTableId(id)) {
            throw ApiException.conflict("Bàn đã có đơn, không xoá được");
        }
        tables.delete(get(id));
        realtime.staffNotice(RealtimeEvent.TABLES_CHANGED);
    }

    /** FR-04.3: the old QR stops working immediately. */
    @Transactional
    public TableDto regenerateQr(Long id) {
        DiningTable table = get(id);
        table.setQrToken(QrTokenGenerator.newToken());
        realtime.staffNotice(RealtimeEvent.TABLES_CHANGED);
        return toDto(table, null);
    }

    private TableDto toDto(DiningTable t, Order open) {
        String qrUrl = props.publicBaseUrl() + "/q/" + t.getQrToken();
        if (open == null) {
            return new TableDto(t.getId(), t.getName(), t.getArea(), t.getSeats(), t.getQrToken(), qrUrl,
                    "AVAILABLE", null, null, 0, 0);
        }
        return new TableDto(t.getId(), t.getName(), t.getArea(), t.getSeats(), t.getQrToken(), qrUrl,
                "OCCUPIED", open.getId(), open.getGuestCount(), open.countItems(ItemStatus.PENDING),
                open.countItems(ItemStatus.READY));
    }

    private void apply(DiningTable table, TableRequest request) {
        table.setName(request.name().trim());
        table.setArea(request.area());
        table.setSeats(request.seats());
    }

    private DiningTable get(Long id) {
        return tables.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy bàn"));
    }
}
