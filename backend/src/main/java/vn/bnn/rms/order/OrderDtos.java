package vn.bnn.rms.order;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(@NotNull OrderType type,
                                     Long tableId,
                                     @Min(1) @Max(100) Integer guestCount,
                                     @Size(max = 500) String note) {
    }

    /** BR-06: 1 to 50 per line. */
    public record ItemLine(@NotNull Long menuItemId,
                           @NotNull @Min(1) @Max(50) Integer quantity,
                           @Size(max = 300) String note) {
    }

    public record AddItemsRequest(@NotEmpty @Size(max = 30) List<@Valid ItemLine> items) {
    }

    public record StatusRequest(@NotNull ItemStatus status) {
    }

    public record CancelRequest(@Size(max = 300) String reason) {
    }

    public record OrderItemDto(Long id, Long menuItemId, String itemName, long unitPrice, int quantity, String note,
                               ItemStatus status, ItemSource source, String cancelReason, Instant createdAt,
                               Instant sentAt, Instant updatedAt) {
        public static OrderItemDto from(OrderItem i) {
            return new OrderItemDto(i.getId(), i.getMenuItem().getId(), i.getItemName(), i.getUnitPrice(),
                    i.getQuantity(), i.getNote(), i.getStatus(), i.getSource(), i.getCancelReason(),
                    i.getCreatedAt(), i.getSentAt(), i.getUpdatedAt());
        }
    }

    /**
     * @param pendingCount  guest dishes waiting for confirmation
     * @param unservedCount dishes still in the kitchen or waiting to be served
     */
    public record OrderDto(Long id, OrderType type, OrderStatus status, Long tableId, String tableName,
                           Integer guestCount, String note, Instant openedAt, Instant closedAt, long total,
                           int pendingCount, int unservedCount, List<OrderItemDto> items) {
        public static OrderDto from(Order o) {
            int unserved = o.countItems(ItemStatus.WAITING) + o.countItems(ItemStatus.COOKING)
                    + o.countItems(ItemStatus.READY);
            return new OrderDto(o.getId(), o.getType(), o.getStatus(), o.tableId(),
                    o.getTable() == null ? null : o.getTable().getName(), o.getGuestCount(), o.getNote(),
                    o.getOpenedAt(), o.getClosedAt(), o.total(), o.countItems(ItemStatus.PENDING), unserved,
                    o.getItems().stream().map(OrderItemDto::from).toList());
        }
    }

    public record KitchenItemDto(Long id, Long orderId, OrderType orderType, String tableName, String itemName,
                                 int quantity, String note, ItemStatus status, Instant sentAt, Instant updatedAt) {
        public static KitchenItemDto from(OrderItem i) {
            Order o = i.getOrder();
            return new KitchenItemDto(i.getId(), o.getId(), o.getType(),
                    o.getTable() == null ? null : o.getTable().getName(), i.getItemName(), i.getQuantity(),
                    i.getNote(), i.getStatus(), i.getSentAt(), i.getUpdatedAt());
        }
    }

    /** What a guest sees about a dish. BR-11: no staff names. */
    public record GuestItemDto(Long id, String itemName, long unitPrice, int quantity, String note,
                               ItemStatus status, String cancelReason) {
        public static GuestItemDto from(OrderItem i) {
            return new GuestItemDto(i.getId(), i.getItemName(), i.getUnitPrice(), i.getQuantity(), i.getNote(),
                    i.getStatus(), i.getCancelReason());
        }
    }

    public record GuestOrderDto(Long orderId, List<GuestItemDto> items, long total, int pendingCount,
                                boolean canPay) {
    }

    /** @param order the table's open order, or null when the table is free */
    public record GuestTableDto(String tableName, String restaurantName, GuestOrderDto order) {
    }
}
