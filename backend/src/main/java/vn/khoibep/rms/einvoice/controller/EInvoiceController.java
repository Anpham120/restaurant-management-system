package vn.khoibep.rms.einvoice.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.einvoice.dto.EInvoiceDtos.BuyerRequest;
import vn.khoibep.rms.einvoice.dto.EInvoiceDtos.EInvoiceDetailDto;
import vn.khoibep.rms.einvoice.dto.EInvoiceDtos.EInvoiceDto;
import vn.khoibep.rms.einvoice.dto.EInvoiceDtos.ExportRequest;
import vn.khoibep.rms.einvoice.dto.EInvoiceDtos.NumberRequest;
import vn.khoibep.rms.einvoice.enums.EInvoiceStatus;
import vn.khoibep.rms.einvoice.service.EInvoiceService;

/** FR-20. Managers keep the e-invoice queue, export it for MISA and record the numbers; cashiers name the buyer. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EInvoiceController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EInvoiceService einvoiceService;

    @GetMapping("/einvoices")
    @PreAuthorize("hasRole('MANAGER')")
    public List<EInvoiceDto> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) EInvoiceStatus status) {
        return einvoiceService.list(from, to, status);
    }

    @GetMapping("/einvoices/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public EInvoiceDetailDto get(@PathVariable Long id) {
        return einvoiceService.get(id);
    }

    @GetMapping("/orders/{id}/einvoice")
    @PreAuthorize("hasRole('CASHIER')")
    public EInvoiceDetailDto ofOrder(@PathVariable Long id) {
        return einvoiceService.ofOrder(id);
    }

    @PutMapping("/einvoices/{id}/buyer")
    @PreAuthorize("hasRole('CASHIER')")
    public EInvoiceDetailDto describeBuyer(@PathVariable Long id, @Valid @RequestBody BuyerRequest request) {
        return einvoiceService.describeBuyer(id, request);
    }

    @PostMapping("/einvoices/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        byte[] file = einvoiceService.export(request.from(), request.to());
        String name = "hoa-don-" + request.from() + "-" + request.to() + ".xlsx";
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(file);
    }

    @PutMapping("/einvoices/{id}/number")
    @PreAuthorize("hasRole('MANAGER')")
    public EInvoiceDetailDto issue(@PathVariable Long id, @Valid @RequestBody NumberRequest request) {
        return einvoiceService.issue(id, request);
    }
}
