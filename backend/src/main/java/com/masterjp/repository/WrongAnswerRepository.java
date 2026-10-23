package com.masterjp.repository;

import com.masterjp.domain.WrongAnswer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

/** F-043: truy van collection wrong_answers. */
public interface WrongAnswerRepository extends MongoRepository<WrongAnswer, String> {

    /** Cau sai chua resolve — moi nhat xep truoc (queue wrong). */
    List<WrongAnswer> findByUserIdAndResolvedFalseOrderByCreatedAtDesc(
            String userId, Pageable pageable);

    /** Phuc vu retention: unresolved cu nhat xep truoc. */
    List<WrongAnswer> findByUserIdAndResolvedFalseOrderByCreatedAtAsc(
            String userId, Pageable pageable);

    /** Dedupe: da co ban ghi unresolved cho cung (userId, kind, wordId)? */
    boolean existsByUserIdAndKindAndWordIdAndResolvedFalse(
            String userId, String kind, String wordId);

    long countByUserIdAndResolvedFalse(String userId);

    /** Lazy don resolved qua 30 ngay khi query queue. */
    void deleteByUserIdAndResolvedTrueAndResolvedAtBefore(String userId, Date cutoff);
}
