package vn.khoibep.rms.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.enums.EInvoiceStatus;
import vn.khoibep.rms.enums.LineKind;
import vn.khoibep.rms.model.EInvoice;
import vn.khoibep.rms.model.EInvoiceLine;

/** E-invoices of paid bills (FR-20). Amounts in VND, rates in percent. */
public final class EInvoiceDtos {

    private EInvoiceDtos() {
    }

    /** Everything blank: a walk-in guest (BR-46). */
    public record BuyerRequest(@Size(max = 200) String name, @Size(max = 20) String taxCode,
                               @Size(max = 300) String address, @Email @Size(max = 100) String email) {
    }

    /** @param number leading zeros are dropped */
    public record NumberRequest(@NotBlank @Size(max = 10) String symbol, @NotBlank @Size(max = 20) String number) {
    }

    /** Days in Vietnam, both included. */
    public record ExportRequest(@NotNull LocalDate from, @NotNull LocalDate to) {
    }

    public record EInvoiceDto(Long id, Long orderId, Instant invoiceDate, String paymentMethod, String buyerName,
                              String buyerTaxCode, String buyerAddress, String buyerEmail, long beforeTax,
                              long taxAmount, long total, EInvoiceStatus status, Instant exportedAt,
                              String invoiceSymbol, String invoiceNo, String issuedByName, Instant issuedAt) {
        public static EInvoiceDto from(EInvoice i) {
            return new EInvoiceDto(i.getId(), i.getOrder().getId(), i.getInvoiceDate(), i.getPaymentMethod(),
                    i.getBuyerName(), i.getBuyerTaxCode(), i.getBuyerAddress(), i.getBuyerEmail(), i.getBeforeTax(),
                    i.getTaxAmount(), i.getTotal(), i.status(), i.getExportedAt(), i.getInvoiceSymbol(),
                    i.getInvoiceNo(), i.getIssuedBy() == null ? null : i.getIssuedBy().getFullName(), i.getIssuedAt());
        }
    }

    /** @param quantity null on a discount line, as are the unit and the unit price */
    public record LineDto(int lineNo, LineKind kind, String itemName, String unit, Integer quantity, Long unitPrice,
                          int taxRate, long amount, long beforeTax, long taxAmount) {
        public static LineDto from(EInvoiceLine l) {
            return new LineDto(l.getLineNo(), l.getKind(), l.getItemName(), l.getUnit(), l.getQuantity(),
                    l.getUnitPrice(), l.getTaxRate(), l.getAmount(), l.getBeforeTax(), l.getTaxAmount());
        }
    }

    /** What one tax rate adds up to, the discounts taken off. */
    public record TaxDto(int taxRate, long beforeTax, long taxAmount) {
    }

    /** @param taxes by rate, the lowest first */
    public record EInvoiceDetailDto(EInvoiceDto invoice, List<LineDto> lines, List<TaxDto> taxes) {
        public static EInvoiceDetailDto from(EInvoice i) {
            Map<Integer, long[]> byRate = new TreeMap<>();
            for (EInvoiceLine l : i.getLines()) {
                long sign = l.getKind() == LineKind.DISCOUNT ? -1 : 1;
                long[] sums = byRate.computeIfAbsent(l.getTaxRate(), rate -> new long[2]);
                sums[0] += sign * l.getBeforeTax();
                sums[1] += sign * l.getTaxAmount();
            }
            return new EInvoiceDetailDto(EInvoiceDto.from(i), i.getLines().stream().map(LineDto::from).toList(),
                    byRate.entrySet().stream().map(e -> new TaxDto(e.getKey(), e.getValue()[0], e.getValue()[1]))
                            .toList());
        }
    }
}
