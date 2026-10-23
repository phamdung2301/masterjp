package com.masterjp.repository;

import com.masterjp.domain.KanjiProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/** F-043: truy van collection kanji_progress. */
public interface KanjiProgressRepository extends MongoRepository<KanjiProgress, String> {

    Optional<KanjiProgress> findByUserIdAndCharacter(String userId, String character);

    /** Kanji den han on: nextReviewAt <= now, qua han lau xep truoc. */
    List<KanjiProgress> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            String userId, Date now, Pageable pageable);

    long countByUserIdAndNextReviewAtLessThanEqual(String userId, Date now);
}
