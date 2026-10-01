package vn.khoibep.rms.report.controller;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.config.AppProperties;
import vn.khoibep.rms.report.dto.ReportDtos.ExceptionsDto;
import vn.khoibep.rms.report.dto.ReportDtos.GrossProfitDto;
import vn.khoibep.rms.report.dto.ReportDtos.SummaryDto;
import vn.khoibep.rms.report.service.ReportService;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AppProperties props;

    /** Defaults to today in Vietnam time. */
    @GetMapping("/summary")
    public SummaryDto summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(props.zoneId());
        return reportService.summary(from == null ? today : from, to == null ? today : to);
    }

    @GetMapping("/gross-profit")
    public GrossProfitDto grossProfit(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.grossProfit(from, to);
    }

    @GetMapping("/exceptions")
    public ExceptionsDto exceptions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.exceptions(from, to);
    }
}
