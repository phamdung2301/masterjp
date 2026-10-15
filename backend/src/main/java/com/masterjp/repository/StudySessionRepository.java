package com.masterjp.repository;

import com.masterjp.domain.StudySession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface StudySessionRepository extends MongoRepository<StudySession, String> {

    List<StudySession> findByUserIdOrderByFinishedAtDesc(String userId, Pageable pageable);

    long countByFinishedAtGreaterThanEqual(Date from);

    long countByFinishedAtBetween(Date from, Date to);
}
