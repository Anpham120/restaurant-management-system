package com.restaurant.core.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.finance.entity.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceIdOrderById(Long invoiceId);

    boolean existsByOrderItemId(Long orderItemId);

    void deleteByInvoiceIdAndOrderItemId(Long invoiceId, Long orderItemId);
}
