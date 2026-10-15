package com.masterjp.service;

import com.masterjp.domain.Lesson;
import com.masterjp.domain.Level;
import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordProgress;
import com.masterjp.domain.WordSection;
import com.masterjp.dto.LessonDtos;
import com.masterjp.dto.WordDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.util.EnumUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Noi dung public read (F-010/F-011): GUEST khong can auth. */
@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final VocabWordRepository vocabWordRepository;
    private final WordProgressRepository wordProgressRepository;

    public LessonService(LessonRepository lessonRepository,
                         VocabWordRepository vocabWordRepository,
                         WordProgressRepository wordProgressRepository) {
        this.lessonRepository = lessonRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.wordProgressRepository = wordProgressRepository;
    }

    /** GET /api/lessons?level= — kem % tu da MASTERED cua user (guest -> 0). */
    public List<LessonDtos.LessonDto> listLessons(Level level, String userId) {
        List<Lesson> lessons = lessonRepository.findByLevelOrderByLessonNoAsc(level);
        List<String> lessonIds = lessons.stream().map(Lesson::getId).toList();
        List<VocabWord> words = lessonIds.isEmpty()
                ? List.of()
                : vocabWordRepository.findByLessonIdInAndActiveTrue(lessonIds);

        Map<String, Long> totalByLesson = words.stream()
                .collect(Collectors.groupingBy(VocabWord::getLessonId, Collectors.counting()));

        Map<String, Long> masteredByLesson = Map.of();
        if (userId != null) {
            Map<String, String> wordToLesson = words.stream()
                    .collect(Collectors.toMap(VocabWord::getId, VocabWord::getLessonId, (a, b) -> a));
            Set<String> masteredWordIds = wordProgressRepository.findByUserId(userId).stream()
                    .filter(p -> p.getStatus() == ProgressStatus.MASTERED)
                    .map(WordProgress::getWordId)
                    .collect(Collectors.toSet());
            masteredByLesson = masteredWordIds.stream()
                    .filter(wordToLesson::containsKey)
                    .collect(Collectors.groupingBy(wordToLesson::get, Collectors.counting()));
        }
        final Map<String, Long> masteredFinal = masteredByLesson;

        return lessons.stream()
                .map(l -> new LessonDtos.LessonDto(
                        l.getId(), l.getLevel().name(), l.getLessonNo(),
                        l.getTitleJa(), l.getTitleVi(), l.getOrder(),
                        totalByLesson.getOrDefault(l.getId(), 0L),
                        masteredFinal.getOrDefault(l.getId(), 0L)))
                .toList();
    }

    public LessonDtos.LessonDetailDto getLesson(String id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                        "Bài học không tồn tại.", HttpStatus.NOT_FOUND));
        return new LessonDtos.LessonDetailDto(lesson.getId(), lesson.getLevel().name(),
                lesson.getLessonNo(), lesson.getTitleJa(), lesson.getTitleVi(), lesson.getOrder());
    }

    /**
     * GET /api/lessons/{id}/words?section= — moi tu kem studyStatus
     * (NEW/LEARNING/MASTERED); guest -> null.
     */
    public List<WordDtos.WordDto> getWords(String lessonId, String sectionStr, String userId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ApiException("LESSON_NOT_FOUND", "Bài học không tồn tại.", HttpStatus.NOT_FOUND);
        }
        WordSection section = EnumUtils.parseOrNull(WordSection.class, sectionStr, "section");
        List<VocabWord> words = section == null
                ? vocabWordRepository.findByLessonIdAndActiveTrueOrderByOrderInLessonAsc(lessonId)
                : vocabWordRepository.findByLessonIdAndSectionAndActiveTrueOrderByOrderInLessonAsc(lessonId, section);

        Map<String, String> statusByWord = Map.of();
        if (userId != null && !words.isEmpty()) {
            List<String> ids = words.stream().map(VocabWord::getId).toList();
            statusByWord = wordProgressRepository.findByUserIdAndWordIdIn(userId, ids).stream()
                    .collect(Collectors.toMap(WordProgress::getWordId,
                            p -> p.getStatus().name(), (a, b) -> a));
        }
        final Map<String, String> statusFinal = statusByWord;
        return words.stream()
                .map(w -> WordMapper.toDto(w, userId == null
                        ? null
                        : statusFinal.getOrDefault(w.getId(), ProgressStatus.NEW.name())))
                .toList();
    }
}
