package com.masterjp.repository;

import com.masterjp.domain.SessionMode;
import com.masterjp.domain.StudySession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface StudySessionRepository extends MongoRepository<StudySession, String> {

    List<StudySession> findByUserIdOrderByFinishedAtDesc(String userId, Pageable pageable);

    long countByFinishedAtGreaterThanEqual(Date from);

    long countByFinishedAtBetween(Date from, Date to);

    // Phase C — F-042 (additive)
    long countByUserId(String userId);

    long countByUserIdAndMode(String userId, SessionMode mode);

    List<StudySession> findByUserIdAndFinishedAtGreaterThanEqual(String userId, Date from);

    List<StudySession> findByFinishedAtBetween(Date from, Date to);

    /**
     * M1 fix (security audit): leaderboard chi quet session cua user da opt-in,
     * khong quet toan bo study_sessions — giam DB-cost amplifier.
     */
    List<StudySession> findByUserIdInAndFinishedAtBetween(java.util.Collection<String> userIds, Date from, Date to);
}
