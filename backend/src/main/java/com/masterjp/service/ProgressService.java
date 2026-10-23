package com.masterjp.service;

import com.masterjp.domain.Lesson;
import com.masterjp.domain.Level;
import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.User;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordProgress;
import com.masterjp.dto.ProgressDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.StudySessionRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tien trinh hoc (F-026/F-027/F-028): SM-2 o backend, ghi ngay sau moi cau
 * (PATCH /api/progress/answer) — thoat app giua chung khong mat du lieu.
 */
@Service
public class ProgressService {

    private final WordProgressRepository wordProgressRepository;
    private final VocabWordRepository vocabWordRepository;
    private final LessonRepository lessonRepository;
    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final FreemiumLimitService freemiumLimitService;

    public ProgressService(WordProgressRepository wordProgressRepository,
                           VocabWordRepository vocabWordRepository,
                           LessonRepository lessonRepository,
                           StudySessionRepository studySessionRepository,
                           UserRepository userRepository,
                           FreemiumLimitService freemiumLimitService) {
        this.wordProgressRepository = wordProgressRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.lessonRepository = lessonRepository;
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
        this.freemiumLimitService = freemiumLimitService;
    }

    /** PATCH /api/progress/answer — upsert theo unique(userId, wordId). */
    public ProgressDtos.AnswerResponse answer(String userId, String wordId, boolean correct) {
        vocabWordRepository.findByIdAndActiveTrue(wordId)
                .orElseThrow(() -> new ApiException("WORD_NOT_FOUND",
                        "Từ vựng không tồn tại.", HttpStatus.NOT_FOUND));

        WordProgress progress = wordProgressRepository.findByUserIdAndWordId(userId, wordId)
                .orElse(null);
        if (progress == null) {
            // Phase D — F-045: "tu moi" = lan dau tao word_progress. Dem TRUOC KHI tao
            // (atomic inc-truoc-check-sau); FREE vuot 20 tu/ngay -> 429 FREEMIUM_LIMIT,
            // tu khong duoc danh dau da hoc. On tap tu cu (update) khong di qua day.
            freemiumLimitService.consumeNewWord(userId);
            WordProgress np = new WordProgress();
            np.setUserId(userId);
            np.setWordId(wordId);
            progress = np;
        }

        Sm2Calculator.apply(progress, correct);
        wordProgressRepository.save(progress);

        double easeFactor = Math.round(progress.getEaseFactor() * 100.0) / 100.0;
        return new ProgressDtos.AnswerResponse(
                progress.getStatus().name(),
                progress.getNextReviewAt(),
                easeFactor,
                progress.getIntervalDays(),
                progress.getRepetitions());
    }

    /** GET /api/progress/due — tu den han, qua han lau xep truoc. */
    public List<ProgressDtos.DueWordDto> dueWords(String userId, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 100);
        List<WordProgress> progresses = wordProgressRepository
                .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        userId, new Date(), PageRequest.of(0, lim));
        if (progresses.isEmpty()) {
            return List.of();
        }
        Map<String, VocabWord> wordById = vocabWordRepository
                .findByIdInAndActiveTrue(progresses.stream().map(WordProgress::getWordId).toList())
                .stream().collect(Collectors.toMap(VocabWord::getId, w -> w, (a, b) -> a));
        Map<String, Integer> lessonNoById = lessonRepository.findAll().stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getLessonNo, (a, b) -> a));

        Date now = new Date();
        List<ProgressDtos.DueWordDto> result = new ArrayList<>();
        for (WordProgress p : progresses) {
            VocabWord w = wordById.get(p.getWordId());
            if (w == null) {
                continue; // tu da bi xoa mem
            }
            long overdueDays = Math.max(0, ChronoUnit.DAYS.between(
                    p.getNextReviewAt().toInstant(), now.toInstant()));
            result.add(new ProgressDtos.DueWordDto(
                    w.getId(), w.getLessonId(), lessonNoById.getOrDefault(w.getLessonId(), 0),
                    w.getSection().name(), w.getKana(), w.getKanji(), w.getRomaji(),
                    w.getMeaningVi(), w.getPos(), w.getExampleJa(), w.getExampleVi(),
                    w.getAudioUrl(), w.getOrderInLesson(), p.getStatus().name(), overdueDays));
        }
        return result;
    }

    /** GET /api/progress/summary — dashboard tien trinh (F-028). */
    public ProgressDtos.ProgressSummaryDto summary(String userId) {
        List<Lesson> lessons = lessonRepository.findAll();
        lessons.sort(Comparator.comparing((Lesson l) -> l.getLevel().ordinal())
                .thenComparing(Lesson::getLessonNo));
        Map<String, Lesson> lessonById = lessons.stream()
                .collect(Collectors.toMap(Lesson::getId, l -> l, (a, b) -> a));

        List<VocabWord> allWords = vocabWordRepository.findByActiveTrue();
        Map<String, List<VocabWord>> wordsByLesson = allWords.stream()
                .collect(Collectors.groupingBy(VocabWord::getLessonId));

        Map<String, WordProgress> progressByWord = wordProgressRepository.findByUserId(userId)
                .stream().collect(Collectors.toMap(WordProgress::getWordId, p -> p, (a, b) -> a));

        Map<String, ProgressDtos.LevelProgressDto> byLevel = new LinkedHashMap<>();
        for (Level level : Level.values()) {
            List<VocabWord> levelWords = allWords.stream()
                    .filter(w -> {
                        Lesson l = lessonById.get(w.getLessonId());
                        return l != null && l.getLevel() == level;
                    }).toList();
            long mastered = levelWords.stream().filter(w -> isMastered(progressByWord.get(w.getId()))).count();
            long learning = levelWords.stream().filter(w -> isLearning(progressByWord.get(w.getId()))).count();
            long total = levelWords.size();
            byLevel.put(level.name(), new ProgressDtos.LevelProgressDto(
                    total, total - mastered - learning, learning, mastered));
        }

        List<ProgressDtos.LessonProgressDto> lessonDtos = lessons.stream()
                .map(l -> {
                    List<VocabWord> ws = wordsByLesson.getOrDefault(l.getId(), List.of());
                    long mastered = ws.stream()
                            .filter(w -> isMastered(progressByWord.get(w.getId()))).count();
                    return new ProgressDtos.LessonProgressDto(l.getId(), l.getLessonNo(),
                            l.getLevel().name(), l.getTitleJa(), l.getTitleVi(),
                            ws.size(), mastered);
                }).toList();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));

        List<ProgressDtos.RecentSessionDto> recentSessions = studySessionRepository
                .findByUserIdOrderByFinishedAtDesc(userId, PageRequest.of(0, 10)).stream()
                .map(s -> new ProgressDtos.RecentSessionDto(s.getId(), s.getMode().name(),
                        s.getScopeType().name(), s.getTotalItems(), s.getCorrectCount(),
                        s.getStartedAt(), s.getFinishedAt()))
                .toList();

        return new ProgressDtos.ProgressSummaryDto(byLevel, lessonDtos,
                user.getStreakDays(), recentSessions);
    }

    private boolean isMastered(WordProgress p) {
        return p != null && p.getStatus() == ProgressStatus.MASTERED;
    }

    private boolean isLearning(WordProgress p) {
        return p != null && p.getStatus() == ProgressStatus.LEARNING;
    }
}
