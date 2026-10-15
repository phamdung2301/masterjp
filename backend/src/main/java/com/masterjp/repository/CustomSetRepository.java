package com.masterjp.repository;

import com.masterjp.domain.CustomSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CustomSetRepository extends MongoRepository<CustomSet, String> {

    List<CustomSet> findByUserIdOrderByUpdatedAtDesc(String userId);

    long countByUserId(String userId);

    Optional<CustomSet> findByIdAndUserId(String id, String userId);

    /** Trang Kham pha (F-035): q rong -> match all. */
    Page<CustomSet> findByIsPublicTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
}
