package vn.khoibep.rms.customer.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.customer.dto.CustomerDtos.AttachRequest;
import vn.khoibep.rms.customer.dto.CustomerDtos.ConsentRequest;
import vn.khoibep.rms.customer.dto.CustomerDtos.CustomerDetailDto;
import vn.khoibep.rms.customer.dto.CustomerDtos.CustomerDto;
import vn.khoibep.rms.customer.dto.CustomerDtos.CustomerRequest;
import vn.khoibep.rms.customer.service.CustomerService;

/** FR-19. Managers see the guest list; cashiers attach a guest to a bill and record what the guest agreed to. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/customers")
    @PreAuthorize("hasRole('MANAGER')")
    public List<CustomerDto> search(@RequestParam(defaultValue = "") String q) {
        return customerService.search(q);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public CustomerDetailDto get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PostMapping("/customers/{id}/consent")
    @PreAuthorize("hasRole('CASHIER')")
    public CustomerDto consent(@PathVariable Long id, @Valid @RequestBody ConsentRequest request) {
        return customerService.consent(id, request);
    }

    @PostMapping("/customers/{id}/opt-out")
    @PreAuthorize("hasRole('CASHIER')")
    public CustomerDto optOut(@PathVariable Long id) {
        return customerService.optOut(id);
    }

    @PostMapping("/orders/{id}/customer")
    @PreAuthorize("hasRole('CASHIER')")
    public CustomerDto attach(@PathVariable Long id, @Valid @RequestBody AttachRequest request) {
        return customerService.attach(id, request);
    }
}
