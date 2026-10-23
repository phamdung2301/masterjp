package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Phase D — F-045: dem tu moi theo user + ngay (Asia/Ho_Chi_Minh).
 * <p>
 * "Tu moi" = thoi diem TAO MOI document word_progress/kanji_progress (lan dau
 * user hoc tu do). On tap tu da co progress KHONG tang counter.
 * <p>
 * Mirror pattern ai_usage cua Phase B: atomic $inc qua findOneAndUpdate
 * (upsert) — 2 request song song khong the cung lot qua khe 20 -> 21.
 * Track cho MOI user (FREE lan PREMIUM) — cung 1 code path, chi FREE bi enforce.
 */
@Document("daily_usage")
@CompoundIndexes({
        @CompoundIndex(name = "uq_daily_usage_user_date", def = "{'userId': 1, 'date': 1}", unique = true)
})
public class DailyUsage {

    @Id
    private String id;

    /** Hex string theo convention codebase (WordProgress.userId, AiUsage.userId). */
    private String userId;

    /** Ngay "yyyy-MM-dd" theo Asia/Ho_Chi_Minh (qua AppTime). */
    private String date;

    /** So tu moi da hoc trong ngay. */
    private long newWordCount;

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

    public long getNewWordCount() {
        return newWordCount;
    }

    public void setNewWordCount(long newWordCount) {
        this.newWordCount = newWordCount;
    }
}
