package vn.khoibep.rms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.dto.TaxDtos.TaxCategoryDto;
import vn.khoibep.rms.dto.TaxDtos.TaxCategoryRequest;
import vn.khoibep.rms.dto.TaxDtos.TaxRateDto;
import vn.khoibep.rms.dto.TaxDtos.TaxRateRequest;
import vn.khoibep.rms.model.TaxCategory;
import vn.khoibep.rms.model.TaxRate;
import vn.khoibep.rms.repository.TaxCategoryRepository;
import vn.khoibep.rms.repository.TaxRateRepository;

/** FR-20.1, BR-45: tax categories of dishes and their VAT rates by day. */
@Service
@RequiredArgsConstructor
public class TaxService {

    /** The VAT rates of Vietnamese law, the cut rate of 8% included. */
    private static final Set<Integer> RATES = Set.of(0, 5, 8, 10);

    private final TaxCategoryRepository categories;
    private final TaxRateRepository rates;
    private final Clock clock;

    /** The rates of every category by day, read once for a whole bill. */
    public record TaxRates(Map<Long, NavigableMap<LocalDate, Integer>> byCategory) {

        /** BR-45: the rate in force on the day; before the first one, the first one. */
        public int on(Long categoryId, LocalDate day) {
            NavigableMap<LocalDate, Integer> days = byCategory.get(categoryId);
            if (days == null || days.isEmpty()) {
                throw new IllegalStateException("Tax category " + categoryId + " has no rate");
            }
            Map.Entry<LocalDate, Integer> inForce = days.floorEntry(day);
            return (inForce != null ? inForce : days.firstEntry()).getValue();
        }
    }

    @Transactional(readOnly = true)
    public TaxRates rates() {
        Map<Long, NavigableMap<LocalDate, Integer>> byCategory = new HashMap<>();
        for (TaxRate r : rates.findAllByOrderByEffectiveFromAsc()) {
            byCategory.computeIfAbsent(r.getCategory().getId(), k -> new TreeMap<>())
                    .put(r.getEffectiveFrom(), r.getRate());
        }
        return new TaxRates(byCategory);
    }

    @Transactional(readOnly = true)
    public List<TaxCategoryDto> categories() {
        TaxRates all = rates();
        LocalDate today = LocalDate.now(clock);
        return categories.findAllByOrderByIdAsc().stream().map(c -> dto(c, all, today)).toList();
    }

    /** A new category, its rate in force from today. */
    @Transactional
    public TaxCategoryDto create(TaxCategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Loại thuế đã tồn tại");
        }
        int rate = valid(request.rate());
        TaxCategory category = categories.save(new TaxCategory(name));
        rates.save(new TaxRate(category, rate, LocalDate.now(clock)));
        return dto(category, rates(), LocalDate.now(clock));
    }

    /** BR-45: the rate from that day on; never before today, so the invoices made keep their rates. */
    @Transactional
    public TaxCategoryDto setRate(Long id, LocalDate from, TaxRateRequest request) {
        TaxCategory category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại thuế"));
        LocalDate today = LocalDate.now(clock);
        if (from.isBefore(today)) {
            throw ApiException.badRequest("Chỉ đặt thuế suất từ hôm nay trở đi");
        }
        int rate = valid(request.rate());
        rates.findByCategoryIdAndEffectiveFrom(id, from).ifPresentOrElse(r -> r.change(rate),
                () -> rates.save(new TaxRate(category, rate, from)));
        return dto(category, rates(), today);
    }

    private static TaxCategoryDto dto(TaxCategory c, TaxRates all, LocalDate today) {
        List<TaxRateDto> days = all.byCategory().get(c.getId()).entrySet().stream()
                .map(e -> new TaxRateDto(e.getValue(), e.getKey())).toList();
        return new TaxCategoryDto(c.getId(), c.getName(), all.on(c.getId(), today), days);
    }

    private static int valid(int rate) {
        if (!RATES.contains(rate)) {
            throw ApiException.badRequest("Thuế suất là 0, 5, 8 hoặc 10%");
        }
        return rate;
    }
}
