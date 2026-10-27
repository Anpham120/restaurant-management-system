package com.restaurant.core.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ConfirmReceiptRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReasonRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptResponse;
import com.restaurant.core.inventory.service.StockDocumentService;
import com.restaurant.core.security.CurrentUser;

/** Phiếu nhập (4.2), xuất + bếp nhận bàn giao (4.3), kiểm kê (4.7). Owner: Đỗ Tuấn Anh. */
@RestController
@RequestMapping("/api")
public class StockDocumentController {

    private final StockDocumentService service;
    private final CurrentUser currentUser;

    public StockDocumentController(StockDocumentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    // ---------- nhập
    @PostMapping("/stock-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ReceiptResponse> createReceipt(@Valid @RequestBody ReceiptRequest request) {
        return ApiResponse.ok(service.createReceipt(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-receipts")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<ReceiptResponse.Header>> receipts(
            @RequestParam(required = false) @Pattern(regexp = "DRAFT|CONFIRMED|CANCELLED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.receipts(status, page, size));
    }

    @GetMapping("/stock-receipts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ReceiptResponse> receipt(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.receipt(id));
    }

    /** Tạo lô RAW cho từng dòng và ghi sổ RECEIPT. receivedAt bỏ trống = bây giờ. */
    @PostMapping("/stock-receipts/{id}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ReceiptResponse> confirmReceipt(@PathVariable @Positive long id,
                                                       @RequestBody(required = false) ConfirmReceiptRequest request) {
        return ApiResponse.ok(service.confirmReceipt(id, currentUser.employeeId(), request == null ? null : request.receivedAt()));
    }

    @DeleteMapping("/stock-receipts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Void> deleteReceipt(@PathVariable @Positive long id) {
        service.deleteDraftReceipt(id);
        return ApiResponse.ok(null);
    }

    // ---------- xuất
    @PostMapping("/stock-issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<IssueResponse> createIssue(@Valid @RequestBody IssueRequest request) {
        return ApiResponse.ok(service.createIssue(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-issues")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<IssueResponse.Header>> issues(
            @RequestParam(required = false) @Pattern(regexp = "DRAFT|ISSUED|RECEIVED|CANCELLED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.issues(status, page, size));
    }

    @GetMapping("/stock-issues/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<IssueResponse> issue(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.issue(id));
    }

    /** Kho xuất: trừ lô RAW (FEFO), hàng chuyển sang trạng thái chờ bếp nhận. */
    @PostMapping("/stock-issues/{id}/issue")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<IssueResponse> issueStock(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.issueStock(id, currentUser.employeeId()));
    }

    /** Bếp xác nhận nhận hàng: dòng DIRECT → lô bếp, dòng PREPARE → WIP chờ sơ chế. */
    @PostMapping("/stock-issues/{id}/receive")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<IssueResponse> receiveIssue(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.receiveIssue(id, currentUser.employeeId()));
    }

    @PostMapping("/stock-issues/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<IssueResponse> cancelIssue(@PathVariable @Positive long id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancelIssue(id, currentUser.employeeId(), request.reason()));
    }

    // ---------- kiểm kê
    @PostMapping("/stock-counts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<CountResponse> createCount(@Valid @RequestBody CountRequest request) {
        return ApiResponse.ok(service.createCount(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-counts")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<CountResponse.Header>> counts(@RequestParam(required = false) @Pattern(regexp = "DRAFT|CONFIRMED|CANCELLED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.counts(status, page, size));
    }

    @GetMapping("/stock-counts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<CountResponse> count(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.count(id));
    }

    /** Ghi chênh lệch COUNT_ADJUST; RC409 nếu số sổ đã đổi kể từ lúc lập phiếu. */
    @PostMapping("/stock-counts/{id}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<CountResponse> confirmCount(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.confirmCount(id, currentUser.employeeId()));
    }
}
