package com.restaurant.core.finance.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.dto.ExpenseRequest;
import com.restaurant.core.finance.dto.ExpenseResponse;
import com.restaurant.core.finance.repository.ExpenseRepository;

/** Ghi và hủy khoản chi hoạt động. Owner: Bùi Đào Đức Anh. */
@Service
public class ExpenseService {

    /** Cho phép lệch đồng hồ nhỏ giữa máy khách và máy chủ. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ExpenseRepository repo;

    public ExpenseService(ExpenseRepository repo) {
        this.repo = repo;
    }

    /** Gửi lặp cùng requestKey → trả khoản chi đã ghi (request_key UNIQUE). */
    public ExpenseResponse create(long actorId, ExpenseRequest r) {
        var existing = repo.findIdByRequestKey(r.requestKey());
        if (existing.isPresent()) {
            return expense(existing.get());
        }
        Instant paidAt = r.paidAt() == null ? Instant.now() : r.paidAt();
        if (paidAt.isAfter(Instant.now().plus(CLOCK_SKEW))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DATA", "Thời điểm chi không được ở tương lai");
        }
        long id = repo.insert(r.requestKey(), r.category(), r.description().strip(), r.amount(), r.paymentMethod(), r.fundSource(),
                r.stockReceiptId(), actorId, paidAt);
        return expense(id);
    }

    public ExpenseResponse voidExpense(long id, long actorId, String reason) {
        if (repo.voidExpense(id, actorId, reason.strip()) == 0) {
            expense(id);
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_VOIDED", "Khoản chi " + id + " đã hủy");
        }
        return expense(id);
    }

    public ExpenseResponse expense(long id) {
        return repo.find(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy khoản chi " + id));
    }

    public List<ExpenseResponse> expenses(LocalDate from, LocalDate to, String category, String status) {
        FinanceReportService.checkRange(from, to);
        return repo.find(from, to, category, status);
    }
}
