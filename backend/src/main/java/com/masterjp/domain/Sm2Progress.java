package com.masterjp.domain;

import java.util.Date;

/**
 * F-043: interface cho tien trinh SM-2 — WordProgress (tu vung) va KanjiProgress
 * (kanji) cung implement de tai su dung {@code Sm2Calculator} ma khong
 * duplicate cong thuc. Chi la view get/set cac field SM-2; khong anh huong
 * mapping MongoDB cua cac domain cu the.
 */
public interface Sm2Progress {

    int getRepetitions();

    void setRepetitions(int repetitions);

    int getIntervalDays();

    void setIntervalDays(int intervalDays);

    double getEaseFactor();

    void setEaseFactor(double easeFactor);

    int getCorrectCount();

    void setCorrectCount(int correctCount);

    int getWrongCount();

    void setWrongCount(int wrongCount);

    int getLapses();

    void setLapses(int lapses);

    ProgressStatus getStatus();

    void setStatus(ProgressStatus status);

    void setLastReviewedAt(Date lastReviewedAt);

    void setNextReviewAt(Date nextReviewAt);
}
