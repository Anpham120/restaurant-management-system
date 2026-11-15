package com.restaurant.core.finance.dto.response;

/** result: PROCESSED (đã thu), MISMATCH (lệch tiền: mở sự cố), AFTER_CANCEL (yêu cầu không còn chờ: mở sự cố). */
public record ManualTransferResponse(String result, InvoiceResponse invoice) {
}
