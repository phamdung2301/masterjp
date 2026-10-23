package com.masterjp.service;

import com.masterjp.domain.DailyUsage;
import com.masterjp.domain.Plan;
import com.masterjp.domain.User;
import com.masterjp.exception.FreemiumLimitException;
import com.masterjp.repository.UserRepository;
import com.masterjp.util.AppTime;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Phase D — F-045: gioi han 20 tu moi/ngay cho plan FREE.
 * <p>
 * Diem chan DUY NHAT: goi {@link #consumeNewWord(String)} NGAY TRUOC KHI tao moi
 * document word_progress/kanji_progress (ProgressService.answer,
 * KanjiProgressService.answerKanji) — "tu moi" = lan dau duoc danh dau "da hoc".
 * On tap tu da co progress (update) khong di qua day.
 * <p>
 * Mirror pattern AiRateLimiter: atomic inc-truoc-check-sau bang findAndModify
 * (upsert) — 2 request song song khong the cung lot qua khe 20 -> 21.
 * Vuot tran -> FreemiumLimitException -> 429 {code: FREEMIUM_LIMIT, limit, resetAt}.
 * <p>
 * Track cho MOI user (FREE lan PREMIUM), chi FREE bi enforce — cung 1 code path.
 * Check SAU khi PlanExpiryService.applyIfExpired: user vua het Premium tinh la FREE ngay.
 */
@Service
public class FreemiumLimitService {

    public static final int FREE_DAILY_LIMIT = 20;

    private final MongoTemplate mongoTemplate;
    private final AppTime appTime;
    private final UserRepository userRepository;
    private final PlanExpiryService planExpiryService;

    public FreemiumLimitService(MongoTemplate mongoTemplate, AppTime appTime,
                                UserRepository userRepository,
                                PlanExpiryService planExpiryService) {
        this.mongoTemplate = mongoTemplate;
        this.appTime = appTime;
        this.userRepository = userRepository;
        this.planExpiryService = planExpiryService;
    }

    /**
     * Tru 1 luot "tu moi" (atomic). Tra ve so tu moi da hoc hom nay (sau khi tru).
     * FREE vuot 20 -> nem FreemiumLimitException; tu do KHONG duoc tao progress.
     */
    public long consumeNewWord(String userId) {
        Plan plan = resolvePlan(userId);

        String date = appTime.today().toString(); // yyyy-MM-dd theo Asia/Ho_Chi_Minh
        Query query = new Query(Criteria.where("userId").is(userId).and("date").is(date));
        Update update = new Update().inc("newWordCount", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        DailyUsage doc = mongoTemplate.findAndModify(query, update, options, DailyUsage.class);
        long count = (doc == null) ? 1 : doc.getNewWordCount();

        if (plan == Plan.FREE && count > FREE_DAILY_LIMIT) {
            throw new FreemiumLimitException(FREE_DAILY_LIMIT, nextResetAt());
        }
        return count;
    }

    private Plan resolvePlan(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Plan.FREE;
        }
        // Han muc check sau applyIfExpired: vua het Premium -> tinh la FREE ngay.
        if (planExpiryService.applyIfExpired(user)) {
            userRepository.save(user);
        }
        return user.getPlan() == null ? Plan.FREE : user.getPlan();
    }

    /** 00:00 ngay hom sau theo Asia/Ho_Chi_Minh, ISO-8601 co offset. */
    private String nextResetAt() {
        ZonedDateTime midnight = appTime.today().plusDays(1).atStartOfDay(appTime.zoneId());
        return midnight.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
