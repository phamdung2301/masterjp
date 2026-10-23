package com.masterjp.service;

import com.masterjp.domain.User;
import com.masterjp.repository.KanjiProgressRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.repository.WrongAnswerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Phase D — F-046: email nhac on tap luc 8:00 sang hang ngay (gio Viet Nam).
 * <p>
 * Doi tuong: user co email da verify + {@code emailReminderOptIn != false}
 * (user hien co — null — duoc coi nhu opt-in, khong can migration) + co it nhat
 * 1 muc den han: word_progress.nextReviewAt <= now HOAC
 * kanji_progress.nextReviewAt <= now HOAC wrong_answers chua resolve.
 * <p>
 * Chong spam: job chay 1 lan/ngay nen moi user toi da 1 email/ngay.
 * [Gia dinh] DOC-13 muc 5.4: v1 KHONG collection email_logs rieng — job
 * idempotent theo ngay nho cron don; neu retry job thu cong trong ngay co the
 * gui trung (chap nhan o v1, khong retry job).
 */
@Service
public class ReviewReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReviewReminderService.class);
    private static final int PAGE_SIZE = 200;

    private final MongoTemplate mongoTemplate;
    private final WordProgressRepository wordProgressRepository;
    private final KanjiProgressRepository kanjiProgressRepository;
    private final WrongAnswerRepository wrongAnswerRepository;
    private final ResendEmailService resendEmailService;

    public ReviewReminderService(MongoTemplate mongoTemplate,
                                 WordProgressRepository wordProgressRepository,
                                 KanjiProgressRepository kanjiProgressRepository,
                                 WrongAnswerRepository wrongAnswerRepository,
                                 ResendEmailService resendEmailService) {
        this.mongoTemplate = mongoTemplate;
        this.wordProgressRepository = wordProgressRepository;
        this.kanjiProgressRepository = kanjiProgressRepository;
        this.wrongAnswerRepository = wrongAnswerRepository;
        this.resendEmailService = resendEmailService;
    }

    /**
     * 8:00:00 sang hang ngay, gio Viet Nam.
     * (Cron 6 field cua Spring: giay-phut-gio-ngay-thang-thu — khong phai 5 field Unix.)
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendDailyReviewReminders() {
        Date now = new Date();
        int page = 0;
        long sent = 0;
        while (true) {
            Query query = new Query(new Criteria().andOperator(
                    Criteria.where("emailVerified").is(true),
                    // null (user cu) = opt-in
                    Criteria.where("emailReminderOptIn").ne(false)))
                    .with(PageRequest.of(page, PAGE_SIZE));
            List<User> users = mongoTemplate.find(query, User.class);
            if (users.isEmpty()) {
                break;
            }
            for (User user : users) {
                long due = wordProgressRepository
                                .countByUserIdAndNextReviewAtLessThanEqual(user.getId(), now)
                        + kanjiProgressRepository
                                .countByUserIdAndNextReviewAtLessThanEqual(user.getId(), now)
                        + wrongAnswerRepository.countByUserIdAndResolvedFalse(user.getId());
                if (due > 0) {
                    // Best-effort: ResendEmailService tu nuot loi, khong lam gay job.
                    resendEmailService.sendReviewReminderEmail(user, due);
                    sent++;
                }
            }
            if (users.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        log.info("Review reminder 8:00: da gui {} email nhac on tap.", sent);
    }
}
