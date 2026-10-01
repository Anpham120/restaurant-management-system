package vn.khoibep.rms.inventory.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.GoodsReceiptDto;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.GoodsReceiptRequest;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.SupplierDto;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.SupplierRequest;
import vn.khoibep.rms.inventory.service.GoodsReceiptService;
import vn.khoibep.rms.inventory.service.SupplierService;

/** FR-09.5 → FR-09.7. Receipts have no edit or delete on purpose (BR-37). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class PurchaseController {

    private final SupplierService supplierService;
    private final GoodsReceiptService receiptService;
    private final CurrentUser currentUser;

    @GetMapping("/suppliers")
    public List<SupplierDto> suppliers() {
        return supplierService.list();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDto createSupplier(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/suppliers/{id}")
    public SupplierDto updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @GetMapping("/goods-receipts")
    public List<GoodsReceiptDto> receipts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return receiptService.list(from, to);
    }

    @GetMapping("/goods-receipts/{id}")
    public GoodsReceiptDto receipt(@PathVariable Long id) {
        return receiptService.get(id);
    }

    @PostMapping("/goods-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsReceiptDto createReceipt(@Valid @RequestBody GoodsReceiptRequest request) {
        return receiptService.create(request, currentUser.id());
    }
}
