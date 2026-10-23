package com.masterjp.repository;

import com.masterjp.domain.SepayTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Phase D — F-044: truy van collection sepay_transactions. */
public interface SepayTransactionRepository extends MongoRepository<SepayTransaction, String> {

    /** Idempotency check: SePay gui lai webhook cung "id". */
    Optional<SepayTransaction> findByBankTransactionId(String bankTransactionId);
}
