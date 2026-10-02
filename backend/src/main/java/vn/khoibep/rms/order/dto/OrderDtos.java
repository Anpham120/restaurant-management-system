package vn.khoibep.rms.order.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.customer.entity.Customer;
import vn.khoibep.rms.order.entity.Adjustment;
import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.entity.OrderItem;
import vn.khoibep.rms.order.entity.ServiceRequest;
import vn.khoibep.rms.order.enums.AdjustmentReason;
import vn.khoibep.rms.order.enums.AdjustmentStatus;
import vn.khoibep.rms.order.enums.AdjustmentType;
import vn.khoibep.rms.order.enums.Channel;
import vn.khoibep.rms.order.enums.ItemSource;
import vn.khoibep.rms.order.enums.ItemStatus;
import vn.khoibep.rms.order.enums.OrderStatus;
import vn.khoibep.rms.order.enums.OrderType;
import vn.khoibep.rms.order.enums.ServiceRequestType;
import vn.khoibep.rms.table.entity.DiningTable;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** @param channel an app order (BR-47), with the code the app gave it */
    public record CreateOrderRequest(@NotNull OrderType type,
                                     Long tableId,
                                     @Min(1) @Max(100) Integer guestCount,
                                     @Size(max = 500) String note,
                                     Channel channel,
                                     @Size(max = 40) String appOrderCode) {
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

    /** BR-36: the tables the order should hold, the first being the main one. */
    public record MoveTablesRequest(@NotEmpty @Size(max = 10) List<@NotNull Long> tableIds) {
    }

    /**
     * FR-08.10: {@code orderItemId} for a dish given free (COMP), {@code amount} for a discount on the whole bill.
     */
    public record AdjustmentRequest(@NotNull AdjustmentType type,
                                    Long orderItemId,
                                    @Min(1) @Max(100_000_000) Long amount,
                                    @NotNull AdjustmentReason reason,
                                    @Size(max = 300) String note) {
    }

    /** @param itemName the dish given free and its quantity; null for a discount on the whole bill */
    public record AdjustmentDto(Long id, Long orderId, String tableName, AdjustmentType type, Long orderItemId,
                                String itemName, long amount, AdjustmentReason reason, String note,
                                AdjustmentStatus status, String createdByName, Instant createdAt,
                                String decidedByName, Instant decidedAt) {
        public static AdjustmentDto from(Adjustment a) {
            Order o = a.getOrder();
            OrderItem item = a.getItem();
            return new AdjustmentDto(a.getId(), o.getId(), o.tableLabel(),
                    a.getType(), item == null ? null : item.getId(),
                    item == null ? null : item.getItemName() + " x" + item.getQuantity(), a.getAmount(),
                    a.getReason(), a.getNote(), a.getStatus(), a.getCreatedBy().getFullName(), a.getCreatedAt(),
                    a.getDecidedBy() == null ? null : a.getDecidedBy().getFullName(), a.getDecidedAt());
        }
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
     * @param customerId    the guest known by phone number (BR-44), or none
     * @param pendingCount  guest dishes waiting for confirmation
     * @param unservedCount dishes still in the kitchen or waiting to be served
     */
    public record OrderDto(Long id, OrderType type, OrderStatus status, Long tableId, List<Long> tableIds,
                           String tableName, Integer guestCount, String note, Channel channel,
                           String appOrderCode, Long customerId,
                           String customerName, String customerPhone, Instant openedAt, Instant closedAt,
                           long subtotal, long discountTotal, long depositCredit, long total, long paidAmount,
                           long due, int pendingCount, int unservedCount,
                           int pendingAdjustmentCount, List<OrderItemDto> items, List<AdjustmentDto> adjustments) {
        public static OrderDto from(Order o) {
            int unserved = o.countItems(ItemStatus.WAITING) + o.countItems(ItemStatus.COOKING)
                    + o.countItems(ItemStatus.READY);
            Customer c = o.getCustomer();
            return new OrderDto(o.getId(), o.getType(), o.getStatus(), o.tableId(),
                    o.activeTables().stream().map(DiningTable::getId).toList(), o.tableLabel(), o.getGuestCount(),
                    o.getNote(), o.getChannel(), o.getAppOrderCode(), c == null ? null : c.getId(),
                    c == null ? null : c.getName(),
                    c == null ? null : c.getPhone(), o.getOpenedAt(), o.getClosedAt(), o.subtotal(), o.discountTotal(), o.depositCredit(), o.total(),
                    o.paidAmount(), o.due(),
                    o.countItems(ItemStatus.PENDING), unserved, o.countPendingAdjustments(),
                    o.getItems().stream().map(OrderItemDto::from).toList(),
                    o.getAdjustments().stream().map(AdjustmentDto::from).toList());
        }
    }

    /** @param channel the app of an app order, shown with its code instead of a table (BR-47) */
    public record KitchenItemDto(Long id, Long orderId, OrderType orderType, Channel channel, String appOrderCode,
                                 String tableName, String itemName, int quantity, String note, ItemStatus status,
                                 Instant sentAt, Instant updatedAt) {
        public static KitchenItemDto from(OrderItem i) {
            Order o = i.getOrder();
            return new KitchenItemDto(i.getId(), o.getId(), o.getType(), o.getChannel(), o.getAppOrderCode(),
                    o.tableLabel(), i.getItemName(), i.getQuantity(),
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

    /** @param discountTotal what the discounts in effect take off; total is what is left to pay (FR-08.10) */
    /**
     * @param depositCredit the deposit of the booking taken off the bill (BR-42)
     * @param paidAmount    what the parts paid so far add up to (BR-43); due is what is left
     */
    public record GuestOrderDto(Long orderId, List<GuestItemDto> items, long discountTotal, long depositCredit,
                                long total, long paidAmount, long due, int pendingCount, boolean canPay) {
    }

    /**
     * @param order        the table's open order, or null when the table is free
     * @param openRequests the table's calls that no waiter has taken yet (FR-06.6)
     */
    public record GuestTableDto(String tableName, String restaurantName, GuestOrderDto order,
                                List<ServiceRequestType> openRequests) {
    }

    public record CallRequest(@NotNull ServiceRequestType type) {
    }

    public record ServiceRequestDto(Long id, Long tableId, String tableName, ServiceRequestType type,
                                    Instant createdAt) {
        public static ServiceRequestDto from(ServiceRequest r) {
            return new ServiceRequestDto(r.getId(), r.getTable().getId(), r.getTable().getName(), r.getType(),
                    r.getCreatedAt());
        }
    }
}
