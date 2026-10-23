package com.masterjp.service;

import com.masterjp.domain.AiGrade;
import com.masterjp.domain.Kanji;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WrongAnswer;
import com.masterjp.dto.ProgressDtos;
import com.masterjp.dto.ReviewDtos;
import com.masterjp.repository.AiGradeRepository;
import com.masterjp.repository.KanjiRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.repository.WrongAnswerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * F-043: GET /api/review/queue — 1 hang doi duy nhat tron 3 nguon:
 *  1. wrong: cau sai chua resolve (wrong_answers), moi nhat xep truoc;
 *  2. vocab den han SM-2 (reuse ProgressService.dueWords);
 *  3. kanji den han SM-2 (KanjiProgressService.dueKanji).
 * Thu tu (binding): wrong truoc, roi vocab/kanji theo overdueDays giam dan.
 * estimatedMinutes = ceil((wrong*15 + vocab*8 + kanji*8) / 60) theo counts
 * CHUA cat limit.
 */
@Service
public class ReviewQueueService {

    private static final int WRONG_SECONDS = 15;
    private static final int DUE_SECONDS = 8;

    private final WrongAnswerRepository wrongAnswerRepository;
    private final WordProgressRepository wordProgressRepository;
    private final VocabWordRepository vocabWordRepository;
    private final AiGradeRepository aiGradeRepository;
    private final KanjiRepository kanjiRepository;
    private final ProgressService progressService;
    private final KanjiProgressService kanjiProgressService;

    public ReviewQueueService(WrongAnswerRepository wrongAnswerRepository,
                              WordProgressRepository wordProgressRepository,
                              VocabWordRepository vocabWordRepository,
                              AiGradeRepository aiGradeRepository,
                              KanjiRepository kanjiRepository,
                              ProgressService progressService,
                              KanjiProgressService kanjiProgressService) {
        this.wrongAnswerRepository = wrongAnswerRepository;
        this.wordProgressRepository = wordProgressRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.aiGradeRepository = aiGradeRepository;
        this.kanjiRepository = kanjiRepository;
        this.progressService = progressService;
        this.kanjiProgressService = kanjiProgressService;
    }

    public ReviewDtos.QueueResponse queue(String userId, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 100);
        Date now = new Date();

        // Retention lazy: resolved=true qua 30 ngay -> don.
        wrongAnswerRepository.deleteByUserIdAndResolvedTrueAndResolvedAtBefore(userId,
                Date.from(Instant.now().minus(WrongAnswer.RESOLVED_RETENTION_DAYS, ChronoUnit.DAYS)));

        List<ReviewDtos.QueueItemDto> items = new ArrayList<>();
        List<WrongAnswer> wrongs = wrongAnswerRepository
                .findByUserIdAndResolvedFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, lim));
        items.addAll(mapWrongItems(wrongs));

        List<DueEntry> dueEntries = new ArrayList<>();
        // vocab den han — reuse logic F-026.
        for (ProgressDtos.DueWordDto w : progressService.dueWords(userId, lim)) {
            dueEntries.add(new DueEntry(w.overdueDays(), toVocabItem(w)));
        }
        // kanji den han — join collection kanji de lay nghia/am doc.
        List<KanjiProgressService.DueKanji> dueKanji = kanjiProgressService.dueKanji(userId, lim);
        if (!dueKanji.isEmpty()) {
            List<String> characters = dueKanji.stream()
                    .map(d -> d.progress().getCharacter()).toList();
            Map<String, Kanji> kanjiById = kanjiRepository.findAllById(characters).stream()
                    .collect(Collectors.toMap(Kanji::getId, Function.identity(), (a, b) -> a));
            for (KanjiProgressService.DueKanji d : dueKanji) {
                Kanji k = kanjiById.get(d.progress().getCharacter());
                if (k == null) {
                    continue; // kanji da bi xoa khoi collection kanji
                }
                dueEntries.add(new DueEntry(d.overdueDays(), new ReviewDtos.QueueItemDto(
                        "kanji", null, null, null, null, null, null, k.getMeaningVi(), null, null,
                        d.overdueDays(), d.progress().getCharacter(), k.getOnYomi(), k.getKunYomi(),
                        null, null, null)));
            }
        }
        // vocab/kanji: qua han lau xep truoc ([Giả định]: overdueDays bang nhau -> vocab truoc).
        dueEntries.sort(Comparator.comparingLong(DueEntry::overdueDays).reversed()
                .thenComparingInt(e -> "vocab".equals(e.item().kind()) ? 0 : 1));
        for (DueEntry e : dueEntries) {
            items.add(e.item());
        }

        List<ReviewDtos.QueueItemDto> page = items.size() > lim ? items.subList(0, lim) : items;

        long wrongCount = wrongAnswerRepository.countByUserIdAndResolvedFalse(userId);
        long vocabCount = wordProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, now);
        long kanjiCount = kanjiProgressService.countDue(userId);
        ReviewDtos.QueueCountsDto counts = new ReviewDtos.QueueCountsDto(
                (int) vocabCount, (int) kanjiCount, (int) wrongCount,
                (int) (vocabCount + kanjiCount + wrongCount));
        int estimatedMinutes = (int) Math.ceil(
                (wrongCount * WRONG_SECONDS + (vocabCount + kanjiCount) * DUE_SECONDS) / 60.0);

        return new ReviewDtos.QueueResponse(List.copyOf(page), counts, estimatedMinutes);
    }

    private List<ReviewDtos.QueueItemDto> mapWrongItems(List<WrongAnswer> wrongs) {
        if (wrongs.isEmpty()) {
            return List.of();
        }
        Map<String, VocabWord> wordById = vocabWordRepository.findByIdInAndActiveTrue(
                        wrongs.stream()
                                .filter(w -> WrongAnswer.KIND_VOCAB.equals(w.getKind())
                                        && w.getWordId() != null)
                                .map(WrongAnswer::getWordId)
                                .toList())
                .stream().collect(Collectors.toMap(VocabWord::getId, Function.identity(), (a, b) -> a));

        List<ReviewDtos.QueueItemDto> items = new ArrayList<>();
        for (WrongAnswer w : wrongs) {
            if (WrongAnswer.KIND_VOCAB.equals(w.getKind())) {
                VocabWord word = w.getWordId() == null ? null : wordById.get(w.getWordId());
                if (word == null) {
                    continue; // tu da bi xoa mem — bo qua item
                }
                items.add(new ReviewDtos.QueueItemDto(
                        "wrong", w.getId(), WrongAnswer.KIND_VOCAB,
                        word.getId(), word.getKana(), word.getKanji(), word.getRomaji(),
                        word.getMeaningVi(), word.getAudioUrl(), w.getCreatedAt(),
                        null, null, null, null, null, null, null));
            } else if (WrongAnswer.KIND_AI_SENTENCE.equals(w.getKind())) {
                AiGrade grade = w.getAiGradeId() == null ? null
                        : aiGradeRepository.findById(w.getAiGradeId()).orElse(null);
                if (grade == null) {
                    // ai_grades bi don boi retention 200 ban ghi -> resolve dangling ref
                    // ([Giả định]: khong render item khong con du lieu goc).
                    w.setResolved(true);
                    w.setResolvedAt(new Date());
                    wrongAnswerRepository.save(w);
                    continue;
                }
                List<ReviewDtos.AiErrorDto> errors = grade.getErrors() == null ? List.of()
                        : grade.getErrors().stream()
                                .map(e -> new ReviewDtos.AiErrorDto(e.getSpan(), e.getIssue(), e.getFix()))
                                .toList();
                items.add(new ReviewDtos.QueueItemDto(
                        "wrong", w.getId(), WrongAnswer.KIND_AI_SENTENCE,
                        null, null, null, null, null, null, null,
                        null, null, null, null,
                        grade.getSentence(), grade.getCorrected(), errors));
            }
        }
        return items;
    }

    private ReviewDtos.QueueItemDto toVocabItem(ProgressDtos.DueWordDto w) {
        return new ReviewDtos.QueueItemDto(
                "vocab", null, null,
                w.id(), w.kana(), w.kanji(), w.romaji(), w.meaningVi(), w.audioUrl(), null,
                w.overdueDays(), null, null, null, null, null, null);
    }

    private record DueEntry(long overdueDays, ReviewDtos.QueueItemDto item) {
    }
}
