package com.masterjp.repository;

import com.masterjp.domain.Lesson;
import com.masterjp.domain.Level;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends MongoRepository<Lesson, String> {

    List<Lesson> findByLevelOrderByLessonNoAsc(Level level);

    Optional<Lesson> findByLevelAndLessonNo(Level level, int lessonNo);
}
