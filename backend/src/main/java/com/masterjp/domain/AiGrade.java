package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase B (F-039) — ket qua AI cham cau viet, de user xem lai loi cu.
 * Chi luu khi grading THANH CONG (HTTP 200); provider loi thi khong luu.
 * Retention: moi user giu toi da 200 ban ghi moi nhat (AiService tu xoa cu nhat khi vuot).
 */
@Document("ai_grades")
@CompoundIndexes({
        @CompoundIndex(name = "idx_ai_grades_user_created", def = "{'userId': 1, 'createdAt': -1}")
})
public class AiGrade {

    @Id
    private String id;

    /** Hex string theo convention codebase (WordProgress.userId, StudySession.userId). */
    private String userId;

    /** Id diem ngu phap duoc cham, vd. "g14-01". */
    private String grammarPointId;

    /** Denormalize tu grammar doc, 1..50. */
    private int lessonNo;

    /** Cau goc cua user (du lieu user — khong tra cho user khac, khong log noi dung). */
    private String sentence;

    /** Diem 0..100. */
    private int score;

    /** Cau da sua hoan chinh. */
    private String corrected;

    private List<ErrorItem> errors = new ArrayList<>();

    /** Giai thich bang tieng Viet. */
    private String explanation;

    /** Provider thuc su da sinh ket qua: "groq" | "openrouter". */
    private String provider;

    private Instant createdAt;

    /** Mot loi trong cau — nested POJO, Mongo map duoc binh thuong. */
    public static class ErrorItem {
        /** Doan trich NGUYEN VAN tu cau goc cua user. */
        private String span;
        private String issue;
        private String fix;

        public ErrorItem() {
        }

        public ErrorItem(String span, String issue, String fix) {
            this.span = span;
            this.issue = issue;
            this.fix = fix;
        }

        public String getSpan() {
            return span;
        }

        public void setSpan(String span) {
            this.span = span;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }

        public String getFix() {
            return fix;
        }

        public void setFix(String fix) {
            this.fix = fix;
        }
    }

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

    public String getGrammarPointId() {
        return grammarPointId;
    }

    public void setGrammarPointId(String grammarPointId) {
        this.grammarPointId = grammarPointId;
    }

    public int getLessonNo() {
        return lessonNo;
    }

    public void setLessonNo(int lessonNo) {
        this.lessonNo = lessonNo;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getCorrected() {
        return corrected;
    }

    public void setCorrected(String corrected) {
        this.corrected = corrected;
    }

    public List<ErrorItem> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorItem> errors) {
        this.errors = errors;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
