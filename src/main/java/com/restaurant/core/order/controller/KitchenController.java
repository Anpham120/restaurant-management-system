package com.restaurant.core.order.controller;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.order.dto.OrderItemResponse;
import com.restaurant.core.order.service.KitchenService;

/** Hàng chờ bếp. Hành động trên từng món ở {@link OrderItemController}. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api/kitchen")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    /** ?status=PENDING&status=ACCEPTED… (mặc định PENDING, ACCEPTED, COOKING, READY). */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<List<OrderItemResponse>> queue(
            @RequestParam(name = "status", required = false)
            List<@Pattern(regexp = "PENDING|ACCEPTED|COOKING|READY") String> statuses) {
        return ApiResponse.ok(kitchenService.queue(statuses));
    }
}
