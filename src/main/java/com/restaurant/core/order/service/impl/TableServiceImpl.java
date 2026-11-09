package com.restaurant.core.order.service.impl;

import java.util.EnumSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.inventory.KitchenStockAllocator;
import com.restaurant.core.order.OrderInvoiceAccess;
import com.restaurant.core.order.dto.response.AbandonResponse;
import com.restaurant.core.order.dto.response.OpenSessionResponse;
import com.restaurant.core.order.dto.response.SessionResponse;
import com.restaurant.core.order.dto.request.TableRequest;
import com.restaurant.core.order.dto.response.TableResponse;
import com.restaurant.core.order.entity.CustomerOrder;
import com.restaurant.core.order.entity.DiningTable;
import com.restaurant.core.order.entity.OrderItem;
import com.restaurant.core.order.entity.OrderItemStatus;
import com.restaurant.core.order.entity.TableSession;
import com.restaurant.core.order.repository.CustomerOrderRepository;
import com.restaurant.core.order.repository.DiningTableRepository;
import com.restaurant.core.order.repository.OrderItemRepository;
import com.restaurant.core.order.repository.TableQueryRepository;
import com.restaurant.core.order.repository.TableSessionRepository;
import com.restaurant.core.order.service.TableService;

/**
 * Bàn và phiên. Thứ tự khóa chung khi đóng phiên: đơn → hóa đơn (+ yêu cầu thanh toán) → phiên → bàn → món,
 * giống chốt hóa đơn và thanh toán, để các thao tác cùng một bàn không chờ nhau vòng tròn.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableServiceImpl implements TableService {

    private final DiningTableRepository diningTableRepository;
    private final TableSessionRepository tableSessionRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TableQueryRepository tableQueryRepository;
    private final OrderItemStatusChanger statusChanger;
    private final KitchenStockAllocator kitchenStockAllocator;
    private final OrderInvoiceAccess invoiceAccess;
    private final BusinessDateProvider clock;

    // ---------- bàn

    @Override
    public List<TableResponse> getAllTables(boolean includeInactive) {
        return tableQueryRepository.findTables(includeInactive);
    }

    @Override
    public TableResponse getTableById(long tableId) {
        return tableQueryRepository.findTable(tableId).orElseThrow(() -> ApiException.notFound("Không tìm thấy bàn " + tableId));
    }

    @Override
    @Transactional
    public TableResponse createTable(TableRequest request) {
        DiningTable table = diningTableRepository.saveAndFlush(new DiningTable(request.code().strip(), request.capacity()));
        return getTableById(table.getId());
    }

    @Override
    @Transactional
    public TableResponse updateTable(long tableId, TableRequest request) {
        DiningTable table = findTableForUpdate(tableId);
        table.changeDetails(request.code().strip(), request.capacity());
        diningTableRepository.flush();
        return getTableById(tableId);
    }

    @Override
    @Transactional
    public TableResponse deactivateTable(long tableId) {
        DiningTable table = findTableForUpdate(tableId);
        if (!table.isAvailable()) {
            throw new ApiException(HttpStatus.CONFLICT, "TABLE_OCCUPIED", "Bàn " + table.getCode() + " đang có khách: đóng phiên trước khi ngừng sử dụng");
        }
        table.deactivate();
        diningTableRepository.flush();
        return getTableById(tableId);
    }

    @Override
    @Transactional
    public TableResponse activateTable(long tableId) {
        findTableForUpdate(tableId).activate();
        diningTableRepository.flush();
        return getTableById(tableId);
    }

    // ---------- phiên

    @Override
    @Transactional
    public OpenSessionResponse openTableSession(long tableId, long actorId) {
        DiningTable table = diningTableRepository.findByIdForUpdate(tableId)
                .filter(DiningTable::isActive)
                .orElseThrow(() -> ApiException.invalidRequest("Bàn " + tableId + " không tồn tại hoặc ngừng sử dụng"));
        if (!table.isAvailable()) {
            throw ApiException.invalidState("Bàn " + table.getCode() + " đang có khách");
        }
        TableSession session = tableSessionRepository.save(new TableSession(table, actorId, clock.now()));
        CustomerOrder order = customerOrderRepository.save(new CustomerOrder(session));
        table.markOccupied();
        diningTableRepository.flush();
        return new OpenSessionResponse(session.getId(), tableId, order.getId());
    }

    @Override
    public SessionResponse getTableSessionById(long sessionId) {
        return tableQueryRepository.findTableSession(sessionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên " + sessionId));
    }

    @Override
    public PageResponse<SessionResponse> getTableSessionHistory(long tableId, int page, int size) {
        getTableById(tableId);
        return new PageResponse<>(tableQueryRepository.findSessionsOfTable(tableId, page, size), page, size,
                tableQueryRepository.countSessionsOfTable(tableId));
    }

    @Override
    @Transactional
    public SessionResponse closeEmptyTableSession(long sessionId, long actorId) {
        CustomerOrder order = findOrderOfSessionForUpdate(sessionId);
        TableSession session = tableSessionRepository.findByIdForUpdate(sessionId).orElseThrow();
        if (!session.isOpen() || !order.isOpen()) {
            throw ApiException.invalidState("Phiên " + sessionId + " không mở");
        }
        if (invoiceAccess.findInvoiceStatus(order.getId()).isPresent()) {
            throw ApiException.invalidState("Phiên " + sessionId + " đã có hóa đơn: đóng bằng thanh toán, miễn thu hoặc xử lý khách bỏ về");
        }
        if (orderItemRepository.existsInOrderWithStatusNotIn(order.getId(), OrderItemStatus.NOT_BILLABLE)) {
            throw ApiException.invalidState("Phiên " + sessionId + " còn món đang xử lý hoặc đã phục vụ");
        }
        DiningTable table = diningTableRepository.findByIdForUpdate(session.getDiningTableId()).orElseThrow();
        order.cancel();
        customerOrderRepository.flush();      // đơn đóng TRƯỚC phiên: DB không cho đóng phiên khi đơn còn mở
        closeSessionAndReleaseTable(session, table, actorId);
        return getTableSessionById(sessionId);
    }

    @Override
    @Transactional
    public AbandonResponse recordCustomerLeftWithoutPaying(long sessionId, long actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalidRequest("Xử lý khách bỏ về bắt buộc có lý do");
        }
        String itemCancelReason = "Khách bỏ về: " + reason.strip();
        CustomerOrder order = findOrderOfSessionForUpdate(sessionId);
        if (!order.isOpen()) {
            throw ApiException.invalidState("Đơn của phiên " + sessionId + " đã " + order.getStatus());
        }
        boolean hasInvoice = invoiceAccess.findInvoiceStatusForUpdate(order.getId()).isPresent();
        TableSession session = tableSessionRepository.findByIdForUpdate(sessionId).orElseThrow();
        if (!session.isOpen()) {
            throw ApiException.invalidState("Phiên " + sessionId + " không mở");
        }
        DiningTable table = diningTableRepository.findByIdForUpdate(session.getDiningTableId()).orElseThrow();

        int servedCount = 0;
        for (OrderItem item : orderItemRepository.findAllOfOrderForUpdate(order.getId())) {
            switch (item.getStatus()) {
                case PENDING, ACCEPTED -> {
                    kitchenStockAllocator.releaseReservationsOfItem(item.getId());
                    item.clearStockException();
                    statusChanger.changeItemStatus(item, EnumSet.of(OrderItemStatus.PENDING, OrderItemStatus.ACCEPTED),
                            OrderItemStatus.CANCELLED, actorId, itemCancelReason);
                }
                case COOKING, READY -> statusChanger.changeItemStatus(item, EnumSet.of(OrderItemStatus.COOKING, OrderItemStatus.READY),
                        OrderItemStatus.CANCELLED, actorId, itemCancelReason);
                case SERVED -> servedCount++;
                default -> { }
            }
        }

        Long invoiceId = null;
        if (servedCount == 0 && !hasInvoice) {
            order.cancel();                                   // không món phải thu, không chứng từ thu
        } else {
            invoiceId = invoiceAccess.recordAbandonedOrderInvoice(order.getId(), actorId, reason.strip());
            order.close();
        }
        customerOrderRepository.flush();
        closeSessionAndReleaseTable(session, table, actorId);
        return new AbandonResponse(sessionId, invoiceId, getTableSessionById(sessionId).invoiceStatus());
    }

    // ---------- hỗ trợ

    private DiningTable findTableForUpdate(long tableId) {
        return diningTableRepository.findByIdForUpdate(tableId).orElseThrow(() -> ApiException.notFound("Không tìm thấy bàn " + tableId));
    }

    private CustomerOrder findOrderOfSessionForUpdate(long sessionId) {
        return customerOrderRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên " + sessionId));
    }

    /** Đóng phiên rồi trả bàn (đúng thứ tự: DB kiểm bàn trống thì không còn phiên mở). */
    private void closeSessionAndReleaseTable(TableSession session, DiningTable table, long actorId) {
        session.close(actorId, clock.now());
        tableSessionRepository.flush();
        table.markAvailable();
        diningTableRepository.flush();
    }
}
