package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.util.Date;

/**
 * F-043: cau tra loi sai duoc ghi lai de dua vao review queue.
 * - kind="vocab": sai trong quiz/flashcard; wordId la id tu vung that
 *   (de FE render lai the/cau hoi).
 * - kind="ai_sentence": Sensei AI cham cau < 60 diem; aiGradeId ref sang ai_grades
 *   (de FE hien lai cau goc + cau sua).
 * Ghi boi hook backend (SessionService / AiService) — khong endpoint rieng.
 * Retention: resolved=false giu toi da 100 ban ghi/user (xoa cu nhat khi vuot);
 * resolved=true qua 30 ngay duoc xoa lazy khi query queue.
 */
@Document("wrong_answers")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_resolved_created",
                def = "{'userId': 1, 'resolved': 1, 'createdAt': -1}"),
        // Phase 1: retention delete (resolved=true, theo resolvedAt) chay moi lan mo tab
        @CompoundIndex(name = "idx_user_resolved_resolvedAt",
                def = "{'userId': 1, 'resolved': 1, 'resolvedAt': 1}")
        // LUU Y: KHONG unique index cho (userId, kind, wordId) — mot tu co the
        // sai lai sau khi da resolve. Dedupe unresolved lam o service (existsBy...).
})
public class WrongAnswer {

    /** kind="vocab" — tu quiz/flashcard. */
    public static final String KIND_VOCAB = "vocab";
    /** kind="ai_sentence" — Sensei AI cham < 60 diem. */
    public static final String KIND_AI_SENTENCE = "ai_sentence";

    /** Gioi han unresolved/user (spec 5.3) — vuot thi xoa cu nhat. */
    public static final int MAX_UNRESOLVED_PER_USER = 100;

    /** resolved=true qua bao nhieu ngay thi duoc don (spec 5.3). */
    public static final int RESOLVED_RETENTION_DAYS = 30;

    @Id
    private String id;

    private String userId;
    private String kind;
    private String wordId; // khi kind="vocab"
    private String aiGradeId; // khi kind="ai_sentence" — ref ai_grades
    private String sessionId; // session gay ra loi (trace, tuy chon)
    private boolean resolved = false;
    private Date createdAt = new Date();
    /** Thoi diem resolve — phuc vu retention (xoa resolved > 30 ngay). */
    private Date resolvedAt;

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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getWordId() {
        return wordId;
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public String getAiGradeId() {
        return aiGradeId;
    }

    public void setAiGradeId(String aiGradeId) {
        this.aiGradeId = aiGradeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Date resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
