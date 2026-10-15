package com.masterjp.repository;

import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordSection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VocabWordRepository extends MongoRepository<VocabWord, String> {

    List<VocabWord> findByLessonIdAndActiveTrueOrderByOrderInLessonAsc(String lessonId);

    List<VocabWord> findByLessonIdAndSectionAndActiveTrueOrderByOrderInLessonAsc(String lessonId, WordSection section);

    /** F-A: loc tu theo cum chu de (co the ket hop section). */
    List<VocabWord> findByLessonIdAndClusterAndActiveTrueOrderByOrderInLessonAsc(String lessonId, String cluster);

    List<VocabWord> findByLessonIdAndSectionAndClusterAndActiveTrueOrderByOrderInLessonAsc(
            String lessonId, WordSection section, String cluster);

    /**
     * F-B: tu chua chu kanji (tim chuoi con) — phuc vu trang chi tiet kanji.
     * Pattern da duoc Pattern.quote o service de tranh regex injection
     * (defense-in-depth; flow hien tai chi truyen ky tu CJK tu DB).
     */
    @Query("{ 'kanji': { $regex: ?0 }, 'active': true }")
    List<VocabWord> findByKanjiSubstringAndActiveTrue(String quotedPattern);

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
