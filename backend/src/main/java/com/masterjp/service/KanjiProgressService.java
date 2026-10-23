package com.masterjp.service;

import com.masterjp.domain.KanjiProgress;
import com.masterjp.dto.ProgressDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.KanjiProgressRepository;
import com.masterjp.repository.KanjiRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * F-043: SM-2 rieng cho kanji (collection kanji_progress).
 * Dung CHUNG {@link Sm2Calculator} voi tu vung (qua interface Sm2Progress) —
 * khong duplicate cong thuc.
 */
@Service
public class KanjiProgressService {

    private final KanjiProgressRepository kanjiProgressRepository;
    private final KanjiRepository kanjiRepository;
    private final FreemiumLimitService freemiumLimitService;

    public KanjiProgressService(KanjiProgressRepository kanjiProgressRepository,
                                KanjiRepository kanjiRepository,
                                FreemiumLimitService freemiumLimitService) {
        this.kanjiProgressRepository = kanjiProgressRepository;
        this.kanjiRepository = kanjiRepository;
        this.freemiumLimitService = freemiumLimitService;
    }

    /**
     * PATCH /api/progress/answer voi kind="kanji" — upsert theo unique(userId, character).
     * Tra cung shape AnswerResponse nhu tu vung.
     */
    public ProgressDtos.AnswerResponse answerKanji(String userId, String character, boolean correct) {
        kanjiRepository.findByCharacter(character)
                .orElseThrow(() -> new ApiException("KANJI_NOT_FOUND",
                        "Kanji không tồn tại.", HttpStatus.NOT_FOUND));

        KanjiProgress progress = kanjiProgressRepository.findByUserIdAndCharacter(userId, character)
                .orElse(null);
        if (progress == null) {
            // Phase D — F-045: "tu moi" = lan dau tao kanji_progress. Dem TRUOC KHI tao
            // (atomic); FREE vuot 20 tu/ngay -> 429 FREEMIUM_LIMIT, kanji khong duoc
            // danh dau da hoc. On tap kanji cu (update) khong di qua day.
            freemiumLimitService.consumeNewWord(userId);
            KanjiProgress np = new KanjiProgress();
            np.setUserId(userId);
            np.setCharacter(character);
            progress = np;
        }

        Sm2Calculator.apply(progress, correct);
        kanjiProgressRepository.save(progress);

        double easeFactor = Math.round(progress.getEaseFactor() * 100.0) / 100.0;
        return new ProgressDtos.AnswerResponse(
                progress.getStatus().name(),
                progress.getNextReviewAt(),
                easeFactor,
                progress.getIntervalDays(),
                progress.getRepetitions());
    }

    /** Kanji den han on — phuc vu GET /api/review/queue. */
    public List<DueKanji> dueKanji(String userId, int limit) {
        int lim = limit <= 0 ? 50 : Math.min(limit, 100);
        Date now = new Date();
        return kanjiProgressRepository
                .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        userId, now, PageRequest.of(0, lim))
                .stream()
                .map(p -> new DueKanji(p, Math.max(0, ChronoUnit.DAYS.between(
                        p.getNextReviewAt().toInstant(), now.toInstant()))))
                .toList();
    }

    /** So kanji den han — phuc vu counts cua queue (khong cat limit). */
    public long countDue(String userId) {
        return kanjiProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, new Date());
    }

    /** Kanji den han kem so ngay qua han. */
    public record DueKanji(KanjiProgress progress, long overdueDays) {
    }
}
