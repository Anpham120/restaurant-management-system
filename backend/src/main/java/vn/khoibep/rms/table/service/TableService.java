package vn.khoibep.rms.table.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.config.AppProperties;
import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.enums.ItemStatus;
import vn.khoibep.rms.order.enums.OrderStatus;
import vn.khoibep.rms.order.repository.OrderRepository;
import vn.khoibep.rms.table.dto.TableDtos.TableDto;
import vn.khoibep.rms.table.dto.TableDtos.TableRequest;
import vn.khoibep.rms.table.entity.DiningTable;
import vn.khoibep.rms.table.repository.DiningTableRepository;

@Service
@RequiredArgsConstructor
public class TableService {

    private final DiningTableRepository tables;
    private final OrderRepository orders;
    private final RealtimeEvents realtime;
    private final AppProperties props;

    /** Floor plan: state is derived from open orders, never stored (BR-04); tables put together share one order. */
    @Transactional(readOnly = true)
    public List<TableDto> list() {
        Map<Long, Order> openByTable = new HashMap<>();
        for (Order order : orders.findWithItemsByStatus(OrderStatus.OPEN)) {
            order.activeTables().forEach(t -> openByTable.put(t.getId(), order));
        }
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
        if (orders.tableEverHeld(id)) {
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
                    "AVAILABLE", null, null, 0, 0, null);
        }
        return new TableDto(t.getId(), t.getName(), t.getArea(), t.getSeats(), t.getQrToken(), qrUrl,
                "OCCUPIED", open.getId(), open.getGuestCount(), open.countItems(ItemStatus.PENDING),
                open.countItems(ItemStatus.READY), open.activeTables().size() > 1 ? open.tableLabel() : null);
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
