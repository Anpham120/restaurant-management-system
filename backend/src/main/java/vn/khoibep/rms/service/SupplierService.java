package vn.khoibep.rms.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.dto.PurchaseDtos.SupplierDto;
import vn.khoibep.rms.dto.PurchaseDtos.SupplierRequest;
import vn.khoibep.rms.model.Supplier;
import vn.khoibep.rms.repository.SupplierRepository;

/** FR-09.5, BR-37: suppliers are never deleted, only stopped. */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository suppliers;

    @Transactional(readOnly = true)
    public List<SupplierDto> list() {
        return suppliers.findAllByOrderByActiveDescNameAsc().stream().map(SupplierDto::from).toList();
    }

    @Transactional
    public SupplierDto create(SupplierRequest request) {
        if (suppliers.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("Nhà cung cấp đã có trong danh sách");
        }
        Supplier supplier = new Supplier();
        apply(supplier, request);
        return SupplierDto.from(suppliers.save(supplier));
    }

    @Transactional
    public SupplierDto update(Long id, SupplierRequest request) {
        if (suppliers.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Nhà cung cấp đã có trong danh sách");
        }
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        apply(supplier, request);
        return SupplierDto.from(supplier);
    }

    private static void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setAddress(blankToNull(request.address()));
        supplier.setTaxCode(blankToNull(request.taxCode()));
        supplier.setNote(blankToNull(request.note()));
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
