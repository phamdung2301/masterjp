package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.util.Date;

/**
 * F-043: tien trinh SM-2 rieng cho kanji (mirror word_progress), thay wordId
 * bang character. Ghi qua PATCH /api/progress/answer voi kind="kanji".
 * Dung chung Sm2Calculator — khong duplicate cong thuc SM-2.
 */
@Document("kanji_progress")
@CompoundIndexes({
        @CompoundIndex(name = "uq_user_character", def = "{'userId': 1, 'character': 1}", unique = true),
        @CompoundIndex(name = "idx_user_next", def = "{'userId': 1, 'nextReviewAt': 1}")
})
public class KanjiProgress implements Sm2Progress {

    @Id
    private String id;

    private String userId;
    /** 1 ky tu kanji, vd. "日" — cung la id cua document trong collection kanji. */
    private String character;
    private ProgressStatus status = ProgressStatus.NEW;
    private double easeFactor = 2.5; // SM-2, min 1.3
    private int intervalDays = 0;
    private int repetitions = 0; // so lan tra loi dung lien tiep
    private int lapses = 0; // so lan quen
    private Date nextReviewAt = new Date();
    private Date lastReviewedAt;
    private int correctCount = 0;
    private int wrongCount = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = character;
    }

    @Override
    public ProgressStatus getStatus() {
        return status;
    }

    @Override
    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    @Override
    public double getEaseFactor() {
        return easeFactor;
    }

    @Override
    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    @Override
    public int getIntervalDays() {
        return intervalDays;
    }

    @Override
    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    @Override
    public int getRepetitions() {
        return repetitions;
    }

    @Override
    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    @Override
    public int getLapses() {
        return lapses;
    }

    @Override
    public void setLapses(int lapses) {
        this.lapses = lapses;
    }

    public Date getNextReviewAt() {
        return nextReviewAt;
    }

    @Override
    public void setNextReviewAt(Date nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public Date getLastReviewedAt() {
        return lastReviewedAt;
    }

    @Override
    public void setLastReviewedAt(Date lastReviewedAt) {
        this.lastReviewedAt = lastReviewedAt;
    }

    @Override
    public int getCorrectCount() {
        return correctCount;
    }

    @Override
    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    @Override
    public int getWrongCount() {
        return wrongCount;
    }

    @Override
    public void setWrongCount(int wrongCount) {
        this.wrongCount = wrongCount;
    }
}
