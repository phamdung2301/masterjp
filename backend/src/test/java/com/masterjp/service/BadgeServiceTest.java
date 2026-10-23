package com.masterjp.service;

import com.masterjp.domain.AiUsage;
import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.SessionMode;
import com.masterjp.domain.StudySession;
import com.masterjp.domain.User;
import com.masterjp.domain.UserBadge;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordProgress;
import com.masterjp.repository.StudySessionRepository;
import com.masterjp.repository.UserBadgeRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.util.AppTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho BadgeService (Phase C — F-042). KHONG can DB/JDK network.
 * Luu y: compile can SessionMode.SHADOWING (C1 them 1 dong enum).
 * Chay bang: mvn test -Dtest=BadgeServiceTest
 */
@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private StudySessionRepository studySessionRepository;
    @Mock private WordProgressRepository wordProgressRepository;
    @Mock private VocabWordRepository vocabWordRepository;
    @Mock private UserRepository userRepository;
    @Mock private MongoTemplate mongoTemplate;

    private final AppTime appTime = new AppTime("Asia/Ho_Chi_Minh");

    private BadgeService service() {
        return new BadgeService(userBadgeRepository, studySessionRepository,
                wordProgressRepository, vocabWordRepository,
                userRepository, mongoTemplate, appTime);
    }

    private static Date at(int hour, int minute) {
        return Date.from(ZonedDateTime.of(2026, 10, 23, hour, minute, 0, 0, ZONE).toInstant());
    }

    private static StudySession session(String userId, SessionMode mode, Date started, Date finished) {
        StudySession s = new StudySession();
        s.setUserId(userId);
        s.setMode(mode);
        s.setStartedAt(started);
        s.setFinishedAt(finished);
        s.setTotalItems(10);
        s.setCorrectCount(8);
        return s;
    }

    private static BadgeService.BadgeStats stats(long sessions, long mastered, int streak,
                                                 int lessons, boolean shadow, long sensei,
                                                 long review, boolean nightOwl) {
        return new BadgeService.BadgeStats(sessions, mastered, streak, lessons,
                shadow, sensei, review, nightOwl);
    }

    // ------------------------------------------------------------------
    // evaluateNewBadges — luat thuan tuy
    // ------------------------------------------------------------------

    @Test
    void firstSessionAwardedOnce() {
        List<String> got = BadgeService.evaluateNewBadges(
                stats(1, 0, 1, 0, false, 0, 0, false), Set.of());
        assertEquals(List.of("FIRST_SESSION"), got);
    }

    @Test
    void alreadyEarnedNotAwardedAgain() {
        List<String> got = BadgeService.evaluateNewBadges(
                stats(5, 0, 3, 0, false, 0, 0, false), Set.of("FIRST_SESSION"));
        assertTrue(got.isEmpty());
    }

    @Test
    void vocabThresholds() {
        assertTrue(BadgeService.evaluateNewBadges(stats(1, 100, 1, 0, false, 0, 0, false), Set.of())
                .containsAll(List.of("FIRST_SESSION", "VOCAB_100")));
        List<String> at500 = BadgeService.evaluateNewBadges(
                stats(1, 500, 1, 0, false, 0, 0, false), Set.of());
        assertTrue(at500.containsAll(List.of("VOCAB_100", "VOCAB_500")));
        // 99 tu -> chua dat VOCAB_100
        assertTrue(BadgeService.evaluateNewBadges(stats(1, 99, 1, 0, false, 0, 0, false), Set.of())
                .stream().noneMatch(c -> c.equals("VOCAB_100")));
    }

    @Test
    void streakThresholds() {
        assertTrue(BadgeService.evaluateNewBadges(stats(1, 0, 7, 0, false, 0, 0, false), Set.of())
                .contains("STREAK_7"));
        assertTrue(BadgeService.evaluateNewBadges(stats(1, 0, 30, 0, false, 0, 0, false), Set.of())
                .containsAll(List.of("STREAK_7", "STREAK_30")));
        assertTrue(BadgeService.evaluateNewBadges(stats(1, 0, 6, 0, false, 0, 0, false), Set.of())
                .stream().noneMatch(c -> c.startsWith("STREAK")));
    }

    @Test
    void lessonThresholds() {
        assertTrue(BadgeService.evaluateNewBadges(stats(1, 0, 1, 5, false, 0, 0, false), Set.of())
                .contains("LESSONS_5"));
        List<String> at50 = BadgeService.evaluateNewBadges(
                stats(1, 0, 1, 50, false, 0, 0, false), Set.of());
        assertTrue(at50.containsAll(List.of("LESSONS_5", "LESSONS_25", "LESSONS_50")));
    }

    @Test
    void shadowSenseiReviewNightOwl() {
        List<String> got = BadgeService.evaluateNewBadges(
                stats(2, 0, 1, 0, true, 10, 100, true),
                Set.of("FIRST_SESSION"));
        assertTrue(got.containsAll(List.of("SHADOW_1", "SENSEI_10", "REVIEW_100", "NIGHT_OWL")));
        // nguong chua dat
        List<String> none = BadgeService.evaluateNewBadges(
                stats(2, 0, 1, 0, false, 9, 99, false), Set.of("FIRST_SESSION"));
        assertTrue(none.stream().noneMatch(
                c -> c.equals("SHADOW_1") || c.equals("SENSEI_10")
                        || c.equals("REVIEW_100") || c.equals("NIGHT_OWL")));
    }

    // ------------------------------------------------------------------
    // onSessionCompleted — tich hop mock
    // ------------------------------------------------------------------

    private void stubBase(String userId, int streakDays) {
        // findByUserId (user_badges) duoc stub RIENG trong tung test de tranh
        // UnnecessaryStubbingException cua Mockito strict stubs.
        when(studySessionRepository.countByUserId(userId)).thenReturn(1L);
        when(wordProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.MASTERED))
                .thenReturn(0L);
        User u = new User();
        u.setId(userId);
        u.setStreakDays(streakDays);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(vocabWordRepository.findByActiveTrue()).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(AiUsage.class)))
                .thenReturn(new ArrayList<>());
        when(mongoTemplate.collectionExists("kanji_progress")).thenReturn(false);
        when(wordProgressRepository.findByUserId(userId)).thenReturn(List.of());
    }

    @Test
    void onSessionCompleted_awardsFirstSession() {
        String userId = "u1";
        stubBase(userId, 1);
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        StudySession s = session(userId, SessionMode.QUIZ_JP_VI, at(10, 0), at(10, 20));

        List<String> awarded = service().onSessionCompleted(s);

        assertEquals(List.of("FIRST_SESSION"), awarded);
        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertEquals("FIRST_SESSION", captor.getValue().getBadgeCode());
        assertEquals(userId, captor.getValue().getUserId());
    }

    @Test
    void onSessionCompleted_awardsNightOwlAfter23h() {
        String userId = "u1";
        stubBase(userId, 1);
        // da co FIRST_SESSION roi -> chi con NIGHT_OWL moi
        UserBadge old = new UserBadge();
        old.setUserId(userId);
        old.setBadgeCode("FIRST_SESSION");
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(old));
        StudySession s = session(userId, SessionMode.QUIZ_JP_VI, at(23, 5), at(23, 40));

        List<String> awarded = service().onSessionCompleted(s);

        assertEquals(List.of("NIGHT_OWL"), awarded);
    }

    @Test
    void onSessionCompleted_22h59NotNightOwl() {
        String userId = "u1";
        stubBase(userId, 1);
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        StudySession s = session(userId, SessionMode.QUIZ_JP_VI, at(22, 30), at(22, 59));

        List<String> awarded = service().onSessionCompleted(s);

        assertEquals(List.of("FIRST_SESSION"), awarded); // khong co NIGHT_OWL
    }

    @Test
    void onSessionCompleted_duplicateKeyIsIdempotent() {
        String userId = "u1";
        stubBase(userId, 1);
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        when(userBadgeRepository.save(any(UserBadge.class)))
                .thenThrow(new DuplicateKeyException("dup key"));
        StudySession s = session(userId, SessionMode.QUIZ_JP_VI, at(10, 0), at(10, 20));

        List<String> awarded = service().onSessionCompleted(s);

        // race: huy hieu da duoc trao boi session song song -> khong tinh la "moi"
        assertTrue(awarded.isEmpty());
    }

    @Test
    void onSessionCompleted_noDoubleSaveWhenAlreadyEarned() {
        String userId = "u1";
        stubBase(userId, 5);
        UserBadge old = new UserBadge();
        old.setUserId(userId);
        old.setBadgeCode("FIRST_SESSION");
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(old));
        StudySession s = session(userId, SessionMode.QUIZ_JP_VI, at(10, 0), at(10, 20));

        List<String> awarded = service().onSessionCompleted(s);

        assertTrue(awarded.isEmpty());
        verify(userBadgeRepository, never()).save(any(UserBadge.class));
    }

    // ------------------------------------------------------------------
    // countCompletedLessons — dinh nghia "done" >= 80% MASTERED
    // ------------------------------------------------------------------

    private static VocabWord word(String id, String lessonId) {
        VocabWord w = new VocabWord();
        w.setId(id);
        w.setLessonId(lessonId);
        return w;
    }

    private static WordProgress mastered(String userId, String wordId) {
        WordProgress p = new WordProgress();
        p.setUserId(userId);
        p.setWordId(wordId);
        p.setStatus(ProgressStatus.MASTERED);
        return p;
    }

    @Test
    void countCompletedLessons_80PercentRule() {
        String userId = "u1";
        // lessonA: 5 tu, 4 mastered (80%) -> done; lessonB: 5 tu, 3 mastered (60%) -> chua
        List<VocabWord> words = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            words.add(word("a" + i, "lessonA"));
            words.add(word("b" + i, "lessonB"));
        }
        when(vocabWordRepository.findByActiveTrue()).thenReturn(words);
        List<WordProgress> progresses = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            progresses.add(mastered(userId, "a" + i));
        }
        for (int i = 1; i <= 3; i++) {
            progresses.add(mastered(userId, "b" + i));
        }
        when(wordProgressRepository.findByUserId(userId)).thenReturn(progresses);

        assertEquals(1, service().countCompletedLessons(userId));
    }
}
