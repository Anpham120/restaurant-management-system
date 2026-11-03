package com.restaurant.core.finance.service;

import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.finance.dto.ExpenseRequest;
import com.restaurant.core.finance.dto.ExpenseResponse;

/** Ghi và hủy khoản chi hoạt động. Owner: Bùi Đào Đức Anh. */
public interface ExpenseService {

    /** Gửi lặp cùng requestKey → trả khoản chi đã ghi (request_key UNIQUE). */
    ExpenseResponse recordExpense(long actorId, ExpenseRequest r);

    ExpenseResponse voidExpense(long id, long actorId, String reason);

    ExpenseResponse getExpenseById(long id);

    List<ExpenseResponse> getExpenses(LocalDate from, LocalDate to, String category, String status);
}
