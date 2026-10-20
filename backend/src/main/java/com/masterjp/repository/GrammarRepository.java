package com.masterjp.repository;

import com.masterjp.domain.Grammar;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Feature: Ngu phap — truy van collection grammar. */
public interface GrammarRepository extends MongoRepository<Grammar, String> {

    /** Chi tiet public: chi tra ve diem con active. */
    Optional<Grammar> findByIdAndActiveTrue(String id);
}
