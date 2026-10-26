package com.restaurant.core.order.dto;

import java.time.Instant;

public record ItemEventResponse(long id, String fromStatus, String toStatus, long actorId, String actorName, String reason,
                                Instant createdAt) {
}
