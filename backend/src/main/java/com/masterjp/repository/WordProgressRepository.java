package com.masterjp.repository;

import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.WordProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface WordProgressRepository extends MongoRepository<WordProgress, String> {

    Optional<WordProgress> findByUserIdAndWordId(String userId, String wordId);

    List<WordProgress> findByUserIdAndWordIdIn(String userId, Collection<String> wordIds);

    /** Tu den han on (F-026): nextReviewAt <= now, qua han lau xep truoc. */
    List<WordProgress> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            String userId, Date now, Pageable pageable);

    List<WordProgress> findByUserId(String userId);

    long countByUserIdAndStatus(String userId, ProgressStatus status);

    /** So tu den han — phuc vu counts cua GET /api/review/queue (F-043, khong cat limit). */
    long countByUserIdAndNextReviewAtLessThanEqual(String userId, Date now);

    /** Top tu bi sai nhieu nhat cho admin (F-034). */
    List<WordProgress> findTop10ByWrongCountGreaterThanOrderByWrongCountDesc(int minWrongCount);
}
