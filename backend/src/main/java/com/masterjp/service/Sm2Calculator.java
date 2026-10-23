package com.masterjp.service;

import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.Sm2Progress;
import com.masterjp.domain.WordProgress;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Thuat toan SM-2 chay O BACKEND (F-026/F-027) — FE chi gui dung/sai de moi
 * client tinh nhat quan.
 *
 * - Dung: repetitions+1; interval = 1 / 6 / x easeFactor (ngay);
 *   MASTERED khi repetitions >= 4 VA interval >= 30 ngay.
 * - Sai: repetitions=0, interval=1 ngay, lapses+1, easeFactor = max(1.3, ef-0.2).
 *
 * Ham thuan (khong DB) de unit-test duoc.
 *
 * F-043: them overload nhan Sm2Progress de kanji (KanjiProgress) dung CHUNG
 * cong thuc nay — khong duplicate. Overload cu (WordProgress) giu nguyen
 * de khong vo code hien co.
 */
public final class Sm2Calculator {

    private Sm2Calculator() {
    }

    /** Giu cho tuong thich nguoc (F-026/F-027) — uy thac sang ban interface. */
    public static void apply(WordProgress progress, boolean correct) {
        apply((Sm2Progress) progress, correct);
    }

    public static void apply(Sm2Progress progress, boolean correct) {
        if (correct) {
            int repetitions = progress.getRepetitions() + 1;
            progress.setRepetitions(repetitions);
            int interval;
            if (repetitions == 1) {
                interval = 1;
            } else if (repetitions == 2) {
                interval = 6;
            } else {
                interval = Math.max(1, (int) Math.round(progress.getIntervalDays() * progress.getEaseFactor()));
            }
            progress.setIntervalDays(interval);
            progress.setCorrectCount(progress.getCorrectCount() + 1);
            if (repetitions >= 4 && interval >= 30) {
                progress.setStatus(ProgressStatus.MASTERED);
            } else {
                progress.setStatus(ProgressStatus.LEARNING);
            }
        } else {
            progress.setRepetitions(0);
            progress.setIntervalDays(1);
            progress.setLapses(progress.getLapses() + 1);
            progress.setEaseFactor(Math.max(1.3, progress.getEaseFactor() - 0.2));
            progress.setWrongCount(progress.getWrongCount() + 1);
            progress.setStatus(ProgressStatus.LEARNING);
        }
        progress.setLastReviewedAt(new Date());
        progress.setNextReviewAt(Date.from(Instant.now().plus(progress.getIntervalDays(), ChronoUnit.DAYS)));
    }
}
