package com.masterjp.repository;

import com.masterjp.domain.AiGrade;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Phase B (F-039) — truy van collection ai_grades. */
public interface AiGradeRepository extends MongoRepository<AiGrade, String> {

    /** Dem so ban ghi cua user, phuc vu retention cap 200. */
    long countByUserId(String userId);
}
