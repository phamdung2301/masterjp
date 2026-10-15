package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Lich su phien hoc — items nhung truc tiep trong document. */
@Document("study_sessions")
public class StudySession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private SessionMode mode;
    private ScopeType scopeType;
    private String scopeId; // lessonId | "lessonId:section" | setId
    private int totalItems;
    private int correctCount;
    private List<SessionItem> items = new ArrayList<>();
    private Date startedAt;
    private Date finishedAt = new Date();

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

    public SessionMode getMode() {
        return mode;
    }

    public void setMode(SessionMode mode) {
        this.mode = mode;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public List<SessionItem> getItems() {
        return items;
    }

    public void setItems(List<SessionItem> items) {
        this.items = items;
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
    }

    public Date getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Date finishedAt) {
        this.finishedAt = finishedAt;
    }

    public static class SessionItem {
        private String wordId;
        private boolean correct;
        private Long timeMs;

        public SessionItem() {
        }

        public SessionItem(String wordId, boolean correct, Long timeMs) {
            this.wordId = wordId;
            this.correct = correct;
            this.timeMs = timeMs;
        }

        public String getWordId() {
            return wordId;
        }

        public void setWordId(String wordId) {
            this.wordId = wordId;
        }

        public boolean isCorrect() {
            return correct;
        }

        public void setCorrect(boolean correct) {
            this.correct = correct;
        }

        public Long getTimeMs() {
            return timeMs;
        }

        public void setTimeMs(Long timeMs) {
            this.timeMs = timeMs;
        }
    }
}
