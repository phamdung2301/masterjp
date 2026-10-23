package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Phase B — Sensei AI: dem quota theo user + ngay (Asia/Ho_Chi_Minh).
 * Tang counter bang atomic $inc qua findOneAndUpdate (upsert) de chong race
 * khi user spam request song song.
 */
@Document("ai_usage")
@CompoundIndexes({
        @CompoundIndex(name = "uq_ai_usage_user_date", def = "{'userId': 1, 'date': 1}", unique = true)
})
public class AiUsage {

    @Id
    private String id;

    /** Hex string theo convention codebase (WordProgress.userId, StudySession.userId). */
    private String userId;

    /** Ngay "yyyy-MM-dd" theo Asia/Ho_Chi_Minh. */
    private String date;

    /** So luot da dung trong ngay. */
    private long used;

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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getUsed() {
        return used;
    }

    public void setUsed(long used) {
        this.used = used;
    }
}
