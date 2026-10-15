package com.masterjp.repository;

import com.masterjp.domain.OtpCode;
import com.masterjp.domain.OtpPurpose;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OtpCodeRepository extends MongoRepository<OtpCode, String> {

    Optional<OtpCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    Optional<OtpCode> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    void deleteByEmailAndPurpose(String email, OtpPurpose purpose);

    void deleteByEmail(String email);
}
