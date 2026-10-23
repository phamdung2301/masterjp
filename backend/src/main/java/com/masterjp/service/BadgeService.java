package com.masterjp.service;

import com.masterjp.domain.AiUsage;
import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.SessionMode;
import com.masterjp.domain.StudySession;
import com.masterjp.domain.UserBadge;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordProgress;
import com.masterjp.repository.StudySessionRepository;
import com.masterjp.repository.UserBadgeRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.util.AppTime;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase C — F-042: trao huy hieu sau moi POST /api/sessions thanh cong.
 * <p>
 * Orchestrator/C1: goi {@link #onSessionCompleted(StudySession)} trong
 * {@code SessionService.create()} NGAY SAU {@code studySessionRepository.save(session)}
 * (xem wiring snippet trong bao cao ban giao C2).
 * <p>
 * Ghi chu merge: can {@code SessionMode.SHADOWING} — C1 da them vao enum
 * trong working tree (branch dung chung), nen file nay compile duoc.
 * <p>
 * Idempotent: unique index (userId, badgeCode) + bat DuplicateKeyException de chong
 * trao trung khi 2 session hoan thanh song song.
 */
@Service
public class BadgeService {

    private final UserBadgeRepository userBadgeRepository;
    private final StudySessionRepository studySessionRepository;
    private final WordProgressRepository wordProgressRepository;
    private final VocabWordRepository vocabWordRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AppTime appTime;

    public BadgeService(UserBadgeRepository userBadgeRepository,
                        StudySessionRepository studySessionRepository,
                        WordProgressRepository wordProgressRepository,
                        VocabWordRepository vocabWordRepository,
                        UserRepository userRepository,
                        MongoTemplate mongoTemplate,
                        AppTime appTime) {
        this.userBadgeRepository = userBadgeRepository;
        this.studySessionRepository = studySessionRepository;
        this.wordProgressRepository = wordProgressRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.appTime = appTime;
    }

    /**
     * Du lieu dau vao thuan tuy cho luat trao huy hieu — de unit test ma khong can DB.
     */
    record BadgeStats(
            long sessionCount,
            long masteredWords,
            int streakDays,
            int completedLessons,
            boolean hasShadowSession,
            long senseiUsage,
            long reviewCorrectTotal,
            boolean nightOwl
    ) {
    }

    /**
     * Hook sau khi session duoc luu thanh cong.
     * Tra ve danh sach code huy hieu VUA dat trong lan nay (co the rong).
     */
    public List<String> onSessionCompleted(StudySession session) {
        String userId = session.getUserId();
        Set<String> already = new HashSet<>();
        for (UserBadge b : userBadgeRepository.findByUserId(userId)) {
            already.add(b.getBadgeCode());
        }
        BadgeStats stats = collectStats(userId, session);
        List<String> awarded = new ArrayList<>();
        for (String code : evaluateNewBadges(stats, already)) {
            UserBadge ub = new UserBadge();
            ub.setUserId(userId);
            ub.setBadgeCode(code);
            ub.setEarnedAt(new Date());
            try {
                userBadgeRepository.save(ub);
                awarded.add(code);
            } catch (DuplicateKeyException e) {
                // Race: session song song vua trao huy hieu nay — bo qua, khong tinh la "moi".
            }
        }
        return awarded;
    }

    /**
     * Luat trao huy hieu thuan tuy — danh sach code dat dieu kien ma chua co.
     * Thu tu theo BadgeCatalog (FIRST_SESSION ... NIGHT_OWL).
     */
    static List<String> evaluateNewBadges(BadgeStats stats, Set<String> alreadyEarned) {
        List<String> out = new ArrayList<>();
        awardIf(out, alreadyEarned, "FIRST_SESSION", stats.sessionCount() >= 1);
        awardIf(out, alreadyEarned, "VOCAB_100", stats.masteredWords() >= 100);
        awardIf(out, alreadyEarned, "VOCAB_500", stats.masteredWords() >= 500);
        awardIf(out, alreadyEarned, "STREAK_7", stats.streakDays() >= 7);
        awardIf(out, alreadyEarned, "STREAK_30", stats.streakDays() >= 30);
        awardIf(out, alreadyEarned, "LESSONS_5", stats.completedLessons() >= 5);
        awardIf(out, alreadyEarned, "LESSONS_25", stats.completedLessons() >= 25);
        awardIf(out, alreadyEarned, "LESSONS_50", stats.completedLessons() >= 50);
        awardIf(out, alreadyEarned, "SHADOW_1", stats.hasShadowSession());
        awardIf(out, alreadyEarned, "SENSEI_10", stats.senseiUsage() >= 10);
        awardIf(out, alreadyEarned, "REVIEW_100", stats.reviewCorrectTotal() >= 100);
        awardIf(out, alreadyEarned, "NIGHT_OWL", stats.nightOwl());
        return out;
    }

    private static void awardIf(List<String> out, Set<String> already, String code, boolean met) {
        if (met && !already.contains(code)) {
            out.add(code);
        }
    }

    private BadgeStats collectStats(String userId, StudySession justCompleted) {
        long sessionCount = studySessionRepository.countByUserId(userId);
        long masteredWords = wordProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.MASTERED);
        int streakDays = userRepository.findById(userId)
                .map(u -> u.getStreakDays())
                .orElse(0);
        int completedLessons = countCompletedLessons(userId);
        // Can C1 them SessionMode.SHADOWING moi compile duoc (xem javadoc class).
        boolean hasShadow = justCompleted.getMode() == SessionMode.SHADOWING
                || studySessionRepository.countByUserIdAndMode(userId, SessionMode.SHADOWING) > 0;
        long senseiUsage = totalSenseiUsage(userId);
        long reviewCorrectTotal = totalReviewCorrect(userId);
        boolean nightOwl = isNightOwl(justCompleted);
        return new BadgeStats(sessionCount, masteredWords, streakDays, completedLessons,
                hasShadow, senseiUsage, reviewCorrectTotal, nightOwl);
    }

    /**
     * Bai "hoan thanh" = co >= 80% tu o trang thai MASTERED
     * (binding DOC-12 muc 4.1 — dinh nghia "done" cua Phase A CaNhan.jsx).
     */
    int countCompletedLessons(String userId) {
        List<VocabWord> words = vocabWordRepository.findByActiveTrue();
        Map<String, List<VocabWord>> byLesson = words.stream()
                .collect(Collectors.groupingBy(VocabWord::getLessonId));
        Map<String, WordProgress> progressByWord = wordProgressRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(WordProgress::getWordId, p -> p, (a, b) -> a));
        int done = 0;
        for (List<VocabWord> ws : byLesson.values()) {
            int total = ws.size();
            if (total == 0) {
                continue;
            }
            long mastered = ws.stream()
                    .filter(w -> {
                        WordProgress p = progressByWord.get(w.getId());
                        return p != null && p.getStatus() == ProgressStatus.MASTERED;
                    })
                    .count();
            if (mastered * 1.0 / total >= 0.8) {
                done++;
            }
        }
        return done;
    }

    /** Tong luot dung Sensei AI cong don moi ngay (khong chi ngay hien tai). */
    long totalSenseiUsage(String userId) {
        return mongoTemplate.find(
                        Query.query(Criteria.where("userId").is(userId)), AiUsage.class)
                .stream()
                .mapToLong(AiUsage::getUsed)
                .sum();
    }

    /**
     * Tong so lan tra loi dung cong don: word_progress.correctCount
     * + kanji_progress.correctCount (neu collection da ton tai — C3 tao).
     */
    long totalReviewCorrect(String userId) {
        long total = wordProgressRepository.findByUserId(userId)
                .stream()
                .mapToLong(WordProgress::getCorrectCount)
                .sum();
        if (mongoTemplate.collectionExists("kanji_progress")) {
            total += mongoTemplate.find(
                            Query.query(Criteria.where("userId").is(userId)),
                            Document.class, "kanji_progress")
                    .stream()
                    .mapToLong(d -> {
                        Object v = d.get("correctCount");
                        return v instanceof Number n ? n.longValue() : 0L;
                    })
                    .sum();
        }
        return total;
    }

    /** Cú đêm: phien hoan thanh sau 23:00 theo gio Asia/Ho_Chi_Minh. */
    boolean isNightOwl(StudySession session) {
        if (session.getFinishedAt() == null) {
            return false;
        }
        int hour = session.getFinishedAt().toInstant().atZone(appTime.zoneId()).getHour();
        return hour >= 23;
    }
}
