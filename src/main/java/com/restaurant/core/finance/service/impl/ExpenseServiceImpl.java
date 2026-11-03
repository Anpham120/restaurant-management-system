package com.restaurant.core.finance.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.dto.ExpenseRequest;
import com.restaurant.core.finance.dto.ExpenseResponse;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.entity.OperatingExpense;
import com.restaurant.core.finance.repository.ExpenseQueryRepository;
import com.restaurant.core.finance.repository.OperatingExpenseRepository;
import com.restaurant.core.finance.service.ExpenseService;

@Service
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {

    /** Cho phép lệch đồng hồ nhỏ giữa máy khách và máy chủ. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ExpenseQueryRepository repo;
    private final OperatingExpenseRepository expenseRepository;
    private final BusinessDateProvider clock;

    public ExpenseServiceImpl(ExpenseQueryRepository repo, OperatingExpenseRepository expenseRepository, BusinessDateProvider clock) {
        this.repo = repo;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    /** Gửi lặp cùng requestKey → trả khoản chi đã ghi (request_key UNIQUE). */
    @Override
    @Transactional
    public ExpenseResponse recordExpense(long actorId, ExpenseRequest r) {
        var existing = expenseRepository.findByRequestKey(r.requestKey());
        if (existing.isPresent()) {
            return getExpenseById(existing.get().getId());
        }
        Instant now = clock.now();
        Instant paidAt = r.paidAt() == null ? now : r.paidAt();
        if (paidAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw ApiException.invalidRequest("Thời điểm chi không được ở tương lai");
        }
        if (OperatingExpense.CASH_DRAWER.equals(r.fundSource()) && !OperatingExpense.CASH.equals(r.paymentMethod())) {
            throw ApiException.invalidRequest("Chi từ két tiền mặt thì phương thức phải là tiền mặt");
        }
        OperatingExpense expense = expenseRepository.saveAndFlush(new OperatingExpense(r.requestKey(), r.category(), r.description().strip(),
                r.amount(), r.paymentMethod(), r.fundSource(), r.stockReceiptId(), actorId, paidAt, BusinessDateProvider.businessDateOf(paidAt), now));
        return getExpenseById(expense.getId());
    }

    @Override
    @Transactional
    public ExpenseResponse voidExpense(long id, long actorId, String reason) {
        OperatingExpense expense = expenseRepository.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản chi " + id));
        if (expense.isVoided()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_VOIDED", "Khoản chi " + id + " đã hủy");
        }
        expense.voidExpense(reason.strip(), actorId, clock.now());
        expenseRepository.flush();
        return getExpenseById(id);
    }

    @Override
    public ExpenseResponse getExpenseById(long id) {
        return repo.find(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy khoản chi " + id));
    }

    @Override
    public List<ExpenseResponse> getExpenses(LocalDate from, LocalDate to, String category, String status) {
        FinanceReportServiceImpl.checkRange(from, to);
        return repo.find(from, to, category, status);
    }
}
