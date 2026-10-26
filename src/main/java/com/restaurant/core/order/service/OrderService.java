package com.restaurant.core.order.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.restaurant.core.order.dto.ItemEventResponse;
import com.restaurant.core.order.dto.OrderItemResponse;
import com.restaurant.core.order.dto.OrderResponse;
import com.restaurant.core.order.dto.SubmitBatchRequest;
import com.restaurant.core.order.dto.SubmitBatchResponse;
import com.restaurant.core.order.repository.OrderRepository;
import com.restaurant.core.order.repository.OrderRepository.OrderRow;
import com.restaurant.core.order.repository.TableRepository;

/** Đơn hàng, gọi món, phục vụ, hủy món (phía Waiter/Manager). Bếp xem {@link KitchenService}. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final TableRepository tables;
    private final JsonMapper json;

    public OrderService(OrderRepository orders, TableRepository tables, JsonMapper json) {
        this.orders = orders;
        this.tables = tables;
        this.json = json;
    }

    public OrderResponse getOrder(long orderId) {
        OrderRow order = orders.findOrder(orderId).orElseThrow(() -> TableService.notFound("Không tìm thấy đơn " + orderId));
        return new OrderResponse(order.id(), order.status(), tables.findSession(order.tableSessionId()).orElseThrow(),
                orders.findItemsOfOrder(orderId));
    }

    /** Gửi một lượt gọi món (lần đầu hay gọi thêm đều là một lượt mới). Cảnh báo hết suất được trả về nguyên vẹn. */
    public SubmitBatchResponse submitBatch(long orderId, long actorId, SubmitBatchRequest request) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SubmitBatchRequest.Item it : request.items()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("daily_menu_item_id", it.dailyMenuItemId());
            m.put("quantity", it.quantity());
            m.put("note", it.note());
            items.add(m);
        }
        JsonNode result = json.readTree(orders.submitBatch(orderId, actorId, request.requestKey(), json.writeValueAsString(items)));
        long batchId = result.get("batch_id").asLong();
        List<SubmitBatchResponse.QuotaWarning> warnings = new ArrayList<>();
        for (JsonNode w : result.get("warnings")) {
            warnings.add(new SubmitBatchResponse.QuotaWarning(w.get("code").asString(), w.get("order_item_id").asLong(),
                    w.get("daily_menu_item_id").asLong(), w.get("item_name").asString(), w.get("quantity").asInt(),
                    w.get("quota_total").asInt(), w.get("quota_used").asLong(), w.get("remaining").asLong()));
        }
        return new SubmitBatchResponse(batchId, result.get("replayed").asBoolean(), warnings, orders.findItemsOfBatch(batchId));
    }

    public OrderItemResponse serve(long itemId, long actorId) {
        orders.serveItem(itemId, actorId);
        return item(itemId);
    }

    public OrderItemResponse cancel(long itemId, long actorId, String reason) {
        orders.cancelItem(itemId, actorId, reason);
        return item(itemId);
    }

    public OrderItemResponse cancelException(long itemId, long actorId, String reason) {
        orders.cancelItemException(itemId, actorId, reason);
        return item(itemId);
    }

    public List<ItemEventResponse> itemEvents(long itemId) {
        item(itemId);
        return orders.findItemEvents(itemId);
    }

    OrderItemResponse item(long itemId) {
        return orders.findItem(itemId).orElseThrow(() -> TableService.notFound("Không tìm thấy món " + itemId));
    }
}
