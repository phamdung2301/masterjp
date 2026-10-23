package com.masterjp.repository;

import com.masterjp.domain.Invoice;
import com.masterjp.domain.InvoiceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/** Phase D — F-044: truy van collection invoices. */
public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    /** Code luu DB luon uppercase — caller uppercase truoc khi goi. */
    Optional<Invoice> findByCode(String code);

    boolean existsByCode(String code);

    List<Invoice> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, InvoiceStatus status);

    List<Invoice> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Phuc vu job quet het han: PENDING tao truoc cutoff. */
    List<Invoice> findByStatusAndCreatedAtBefore(InvoiceStatus status, Date cutoff);
}
