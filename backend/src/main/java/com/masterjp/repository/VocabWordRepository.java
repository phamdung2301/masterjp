package com.masterjp.repository;

import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordSection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VocabWordRepository extends MongoRepository<VocabWord, String> {

    List<VocabWord> findByLessonIdAndActiveTrueOrderByOrderInLessonAsc(String lessonId);

    List<VocabWord> findByLessonIdAndSectionAndActiveTrueOrderByOrderInLessonAsc(String lessonId, WordSection section);

    Optional<VocabWord> findByIdAndActiveTrue(String id);

    List<VocabWord> findByIdInAndActiveTrue(Collection<String> ids);

    List<VocabWord> findByLessonIdInAndActiveTrue(Collection<String> lessonIds);

    List<VocabWord> findByActiveTrue();

    /** Upsert key cho import Excel: kana + lessonId + section (F-032). */
    Optional<VocabWord> findByLessonIdAndSectionAndKanaAndActiveTrue(String lessonId, WordSection section, String kana);

    long countByLessonIdAndActiveTrue(String lessonId);

    long countByActiveTrue();

    /** Tu co orderInLesson lon nhat cua bai (phuc vu import Excel gan thu tu noi tiep). */
    Optional<VocabWord> findTopByLessonIdOrderByOrderInLessonDesc(String lessonId);

    /** Tim kiem chinh xac (uu tien hien thi truoc o F-012). */
    List<VocabWord> findByKana(String kana);

    List<VocabWord> findByKanji(String kanji);
}
