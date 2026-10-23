package com.masterjp.service;

import com.masterjp.domain.AiUsage;
import com.masterjp.dto.AiDtos;
import com.masterjp.exception.QuotaExceededException;
import com.masterjp.util.AppTime;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Phase B — Sensei AI rate limiter: quota theo user + ngay (Asia/Ho_Chi_Minh),
 * luu o Mongo collection {@code ai_usage} (an toan khi Render free tier restart/sleep,
 * multi-instance).
 * <p>
 * {@link #consume(String)}: atomic check-and-increment (findOneAndUpdate upsert + $inc)
 * ngay sau khi request qua validation, TRUOC khi goi provider — chong bypass quota
 * bang cach ep provider loi de retry mien phi. Neu used moi > limit -> 429
 * (giu nguyen so da tang — over-count nhe duoc chap nhan theo spec).
 */
@Service
public class AiRateLimiter {

    public static final int DEFAULT_LIMIT = 50;

    private final MongoTemplate mongoTemplate;
    private final AppTime appTime;
    private final int dailyLimit;

    public AiRateLimiter(MongoTemplate mongoTemplate, AppTime appTime, Environment env) {
        this.mongoTemplate = mongoTemplate;
        this.appTime = appTime;
        this.dailyLimit = resolveLimit(env);
    }

    private static int resolveLimit(Environment env) {
        String raw = env == null ? null : env.getProperty("AI_DAILY_LIMIT_PER_USER");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : DEFAULT_LIMIT;
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    /**
     * Tru 1 quota (atomic). Tra ve quota sau khi tru; het quota -> nem
     * {@link QuotaExceededException} (429 {code: DAILY_LIMIT, message, resetAt}).
     */
    public AiDtos.QuotaResponse consume(String userId) {
        String date = appTime.today().toString(); // yyyy-MM-dd theo Asia/Ho_Chi_Minh
        Query query = new Query(Criteria.where("userId").is(userId).and("date").is(date));
        Update update = new Update().inc("used", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        AiUsage doc = mongoTemplate.findAndModify(query, update, options, AiUsage.class);
        long used = (doc == null) ? 1 : doc.getUsed();
        if (used > dailyLimit) {
            throw new QuotaExceededException(
                    "Bạn đã dùng hết " + dailyLimit + " lượt Sensei AI hôm nay. Quota mới vào lúc 00:00.",
                    nextResetAt());
        }
        return toQuota(used);
    }

    /** Doc quota hien tai — KHONG tru quota (dung cho GET /api/ai/quota). */
    public AiDtos.QuotaResponse readQuota(String userId) {
        String date = appTime.today().toString();
        AiUsage doc = mongoTemplate.findOne(
                new Query(Criteria.where("userId").is(userId).and("date").is(date)),
                AiUsage.class);
        long used = (doc == null) ? 0 : doc.getUsed();
        return toQuota(used);
    }

    private AiDtos.QuotaResponse toQuota(long used) {
        int remaining = (int) Math.max(0, dailyLimit - used);
        return new AiDtos.QuotaResponse(dailyLimit, (int) used, remaining, nextResetAt());
    }

    /** 00:00 ngay hom sau theo Asia/Ho_Chi_Minh, ISO-8601 (vd. "2026-10-23T00:00:00+07:00"). */
    private String nextResetAt() {
        ZonedDateTime midnight = appTime.today().plusDays(1).atStartOfDay(appTime.zoneId());
        return midnight.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
