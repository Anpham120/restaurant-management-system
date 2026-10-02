package vn.khoibep.rms.menu.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.menu.dto.TaxDtos.TaxCategoryDto;
import vn.khoibep.rms.menu.dto.TaxDtos.TaxCategoryRequest;
import vn.khoibep.rms.menu.dto.TaxDtos.TaxRateRequest;
import vn.khoibep.rms.menu.service.TaxService;

/** FR-20.1: managers keep the tax categories of dishes and their rates by day. */
@RestController
@RequestMapping("/api/tax-categories")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public List<TaxCategoryDto> categories() {
        return taxService.categories();
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxCategoryDto create(@Valid @RequestBody TaxCategoryRequest request) {
        return taxService.create(request);
    }

    @PutMapping("/{id}/rates/{from}")
    @PreAuthorize("hasRole('MANAGER')")
    public TaxCategoryDto setRate(@PathVariable Long id,
                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @Valid @RequestBody TaxRateRequest request) {
        return taxService.setRate(id, from, request);
    }
}
