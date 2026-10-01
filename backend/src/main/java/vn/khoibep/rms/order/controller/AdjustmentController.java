package vn.khoibep.rms.order.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.order.dto.OrderDtos.AdjustmentDto;
import vn.khoibep.rms.order.dto.OrderDtos.AdjustmentRequest;
import vn.khoibep.rms.order.dto.OrderDtos.OrderDto;
import vn.khoibep.rms.order.enums.AdjustmentStatus;
import vn.khoibep.rms.order.service.AdjustmentService;

/** FR-08.10, FR-08.11: cashiers give discounts, managers decide the ones past the limit (BR-35). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    @PostMapping("/orders/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CASHIER')")
    public OrderDto create(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest request) {
        return adjustmentService.create(id, request);
    }

    @PostMapping("/adjustments/{id}/cancel")
    @PreAuthorize("hasRole('CASHIER')")
    public OrderDto cancel(@PathVariable Long id) {
        return adjustmentService.cancel(id);
    }

    /** Only the pending list exists: decided adjustments are read with their order. */
    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('MANAGER')")
    public List<AdjustmentDto> list(@RequestParam AdjustmentStatus status) {
        if (status != AdjustmentStatus.PENDING) {
            throw ApiException.badRequest("Chỉ xem được danh sách chờ duyệt");
        }
        return adjustmentService.pending();
    }

    @PostMapping("/adjustments/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public AdjustmentDto approve(@PathVariable Long id) {
        return adjustmentService.approve(id);
    }

    @PostMapping("/adjustments/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public AdjustmentDto reject(@PathVariable Long id) {
        return adjustmentService.reject(id);
    }
}
