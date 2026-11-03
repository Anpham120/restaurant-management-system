package com.restaurant.core.order.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.order.OrderBillingApi;
import com.restaurant.core.order.entity.CustomerOrder;
import com.restaurant.core.order.entity.DiningTable;
import com.restaurant.core.order.entity.OrderItemStatus;
import com.restaurant.core.order.entity.TableSession;
import com.restaurant.core.order.repository.CustomerOrderRepository;
import com.restaurant.core.order.repository.DiningTableRepository;
import com.restaurant.core.order.repository.OrderItemRepository;
import com.restaurant.core.order.repository.TableSessionRepository;

/** Cài đặt cho module Tài chính. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderBillingApiImpl implements OrderBillingApi {

    private final CustomerOrderRepository customerOrderRepository;
    private final TableSessionRepository tableSessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final OrderItemRepository orderItemRepository;
    private final BusinessDateProvider clock;

    @Override
    public OrderForBilling lockOrderForBilling(long orderId) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn " + orderId));
        return new OrderForBilling(order.getId(), order.isOpen(), order.getTableSessionId());
    }

    @Override
    public boolean hasUnfinishedItems(long orderId) {
        return orderItemRepository.existsInOrderWithStatusNotIn(orderId,
                java.util.EnumSet.of(OrderItemStatus.SERVED, OrderItemStatus.CANCELLED, OrderItemStatus.REJECTED));
    }

    @Override
    public List<ServedItem> getServedItems(long orderId) {
        return orderItemRepository.findAllOfOrder(orderId).stream()
                .filter(i -> i.getStatus() == OrderItemStatus.SERVED)
                .map(i -> new ServedItem(i.getId(), i.getItemNameSnapshot(), i.getQuantity(), i.getUnitPrice()))
                .toList();
    }

    @Override
    public void lockSessionAndTable(long orderId) {
        CustomerOrder order = customerOrderRepository.findById(orderId).orElseThrow();
        TableSession session = tableSessionRepository.findByIdForUpdate(order.getTableSessionId()).orElseThrow();
        diningTableRepository.findByIdForUpdate(session.getDiningTableId()).orElseThrow();
    }

    @Override
    public void closeOrderAndReleaseTable(long orderId, long actorId) {
        CustomerOrder order = customerOrderRepository.findById(orderId).orElseThrow();
        order.close();
        customerOrderRepository.flush();
        TableSession session = tableSessionRepository.findByIdForUpdate(order.getTableSessionId()).orElseThrow();
        session.close(actorId, clock.now());
        tableSessionRepository.flush();
        DiningTable table = diningTableRepository.findByIdForUpdate(session.getDiningTableId()).orElseThrow();
        table.markAvailable();
        diningTableRepository.flush();
    }
}
