package com.masterjp.repository;

import com.masterjp.domain.Kanji;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** F-B (Feature Pack 2): truy van collection kanji. */
public interface KanjiRepository extends MongoRepository<Kanji, String> {

    /** Kanji cua 1 bai (lessonNos la List -> Containing = phan tu co trong list). */
    List<Kanji> findByLessonNosContainingOrderByStrokesAsc(Integer lessonNo);

    List<Kanji> findByJlptOrderByStrokesAsc(String jlpt);

    List<Kanji> findByLessonNosContainingAndJlptOrderByStrokesAsc(Integer lessonNo, String jlpt);

    List<Kanji> findAllByOrderByStrokesAsc();

    Optional<Kanji> findByCharacter(String character);

    /** Dem so chu theo topic (cho GET /api/kanji/topics). */
    long countByTopic(String topic);
}
