package vn.khoibep.rms.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.dto.OrderDtos.ServiceRequestDto;
import vn.khoibep.rms.service.ServiceRequestService;

/** Guest calls as waiters see them (FR-06.6, FR-06.7). */
@RestController
@RequestMapping("/api/service-requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService serviceRequests;

    @GetMapping
    @PreAuthorize("hasRole('WAITER')")
    public List<ServiceRequestDto> open() {
        return serviceRequests.open();
    }

    @PostMapping("/{id}/take")
    @PreAuthorize("hasRole('WAITER')")
    public void take(@PathVariable Long id) {
        serviceRequests.take(id);
    }
}
