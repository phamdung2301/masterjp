package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.document.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.util.Date;

/**
 * Tien trinh tung tu cua tung user — trai tim cua SRS (SM-2 chay o backend).
 */
@Document("word_progress")
@CompoundIndexes({
        @CompoundIndex(name = "uq_user_word", def = "{'userId': 1, 'wordId': 1}", unique = true),
        @CompoundIndex(name = "idx_user_next", def = "{'userId': 1, 'nextReviewAt': 1}")
})
public class WordProgress {

    @Id
    private String id;

    private String userId;
    private String wordId;
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

    public String getWordId() {
        return wordId;
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public int getLapses() {
        return lapses;
    }

    public void setLapses(int lapses) {
        this.lapses = lapses;
    }

    public Date getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(Date nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public Date getLastReviewedAt() {
        return lastReviewedAt;
    }

    public void setLastReviewedAt(Date lastReviewedAt) {
        this.lastReviewedAt = lastReviewedAt;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(int wrongCount) {
        this.wrongCount = wrongCount;
    }
}
