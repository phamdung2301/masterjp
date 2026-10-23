package com.masterjp.service;

import com.masterjp.domain.StudySession;
import com.masterjp.domain.User;
import com.masterjp.domain.UserBadge;
import com.masterjp.dto.GamificationDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.StudySessionRepository;
import com.masterjp.repository.UserBadgeRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.util.AppTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho GamificationService (Phase C — F-042). KHONG can DB.
 * Chay bang: mvn test -Dtest=GamificationServiceTest
 */
@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudySessionRepository studySessionRepository;

    private final AppTime appTime = new AppTime("Asia/Ho_Chi_Minh");

    private GamificationService service() {
        return new GamificationService(userBadgeRepository, userRepository,
                studySessionRepository, appTime);
    }

    // ------------------------------------------------------------------
    // parseWeek
    // ------------------------------------------------------------------

    @Test
    void parseWeek_valid() {
        GamificationService.WeekRange r = GamificationService.parseWeek("2026-W43", ZONE);
        assertEquals("2026-W43", r.week());
        assertEquals("2026-10-19T00:00:00+07:00", r.weekStartIso());
        assertEquals("2026-10-25T23:59:59+07:00", r.weekEndIso());
        assertEquals(LocalDate.of(2026, 10, 19),
                r.start().toInstant().atZone(ZONE).toLocalDate());
    }

    @Test
    void parseWeek_defaultIsCurrentWeek() {
        GamificationService.WeekRange r = GamificationService.parseWeek(null, ZONE);
        LocalDate monday = LocalDate.now(ZONE).with(WeekFields.ISO.dayOfWeek(), 1);
        assertTrue(r.week().matches("^\\d{4}-W\\d{2}$"));
        assertEquals(monday, r.start().toInstant().atZone(ZONE).toLocalDate());
        assertEquals(monday, LocalDate.parse(r.weekStartIso().substring(0, 10)));
    }

    @Test
    void parseWeek_invalidFormats() {
        for (String bad : new String[]{"2026-43", "W43", "2026-W4", "abc",
                "2026-W00", "2026-W54", "2025-W53", " 2026-W43x "}) {
            ApiException e = assertThrows(ApiException.class,
                    () -> GamificationService.parseWeek(bad, ZONE), "week=" + bad);
            assertEquals("VALIDATION_ERROR", e.getCode());
        }
    }

    @Test
    void parseWeek_53ExistsIn2026() {
        // 2026 co 53 tuan ISO (1/1/2026 la Thu) -> W53 hop le
        GamificationService.WeekRange r = GamificationService.parseWeek("2026-W53", ZONE);
        assertEquals(LocalDate.of(2026, 12, 28),
                r.start().toInstant().atZone(ZONE).toLocalDate());
    }

    // ------------------------------------------------------------------
    // xpFor / effectiveNickname
    // ------------------------------------------------------------------

    @Test
    void xpFormula() {
        assertEquals(140, GamificationService.xpFor(10, 20)); // 10*10 + 20*2
        assertEquals(0, GamificationService.xpFor(0, 0));
    }

    @Test
    void effectiveNickname() {
        User named = new User();
        named.setId("64f000000000000000000001");
        named.setNickname("Hana-chan");
        assertEquals("Hana-chan", GamificationService.effectiveNickname(named));

        User unnamed = new User();
        unnamed.setId("64f00000000000000000abcd");
        assertEquals("Tanu-abcd", GamificationService.effectiveNickname(unnamed));
    }

    // ------------------------------------------------------------------
    // badges
    // ------------------------------------------------------------------

    @Test
    void badges_returnsAll12WithEarnedState() {
        UserBadge earned = new UserBadge();
        earned.setUserId("u1");
        earned.setBadgeCode("FIRST_SESSION");
        earned.setEarnedAt(new Date(1_000_000L));
        when(userBadgeRepository.findByUserId("u1")).thenReturn(List.of(earned));

        GamificationDtos.BadgesResponse res = service().badges("u1");

        assertEquals(12, res.totalCount());
        assertEquals(12, res.badges().size());
        assertEquals(1, res.earnedCount());
        GamificationDtos.BadgeDto first = res.badges().get(0);
        assertEquals("FIRST_SESSION", first.code());
        assertEquals("始", first.icon());
        assertTrue(first.earned());
        assertNotNull(first.earnedAt());
        GamificationDtos.BadgeDto second = res.badges().get(1);
        assertEquals("VOCAB_100", second.code());
        assertFalse(second.earned());
        assertNull(second.earnedAt());
    }

    // ------------------------------------------------------------------
    // heatmap
    // ------------------------------------------------------------------

    private static StudySession sessionOn(String userId, LocalDate day, int minutes) {
        StudySession s = new StudySession();
        s.setUserId(userId);
        Date start = Date.from(day.atStartOfDay(ZONE).toInstant());
        Date end = new Date(start.getTime() + minutes * 60_000L);
        s.setStartedAt(start);
        s.setFinishedAt(end);
        return s;
    }

    @Test
    void heatmap_fullWeeksAndZeroDays() {
        String userId = "u1";
        LocalDate today = appTime.today();
        LocalDate twoDaysAgo = today.minusDays(2);
        List<StudySession> sessions = new ArrayList<>();
        sessions.add(sessionOn(userId, today, 25));
        sessions.add(sessionOn(userId, today, 20)); // cung ngay -> 45 phut
        sessions.add(sessionOn(userId, twoDaysAgo, 2)); // 2 phut -> round = 2
        when(studySessionRepository.findByUserIdAndFinishedAtGreaterThanEqual(
                any(String.class), any(Date.class))).thenReturn(sessions);

        GamificationDtos.HeatmapResponse res = service().heatmap(userId, 12);

        assertEquals(12, res.weeks());
        assertEquals(84, res.days().size());
        // ngay cuoi = hom nay
        GamificationDtos.HeatmapDayDto last = res.days().get(83);
        assertEquals(today.toString(), last.date());
        assertEquals(45, last.minutes());
        assertEquals(2, last.sessions());
        // ngay hom qua: khong hoc -> 0
        GamificationDtos.HeatmapDayDto yesterday = res.days().get(82);
        assertEquals(0, yesterday.minutes());
        assertEquals(0, yesterday.sessions());
        // 2 ngay truoc: 2 phut
        assertEquals(2, res.days().get(81).minutes());
        // ngay dau tien cua cua so 84 ngay
        assertEquals(today.minusDays(83).toString(), res.days().get(0).date());
    }

    @Test
    void heatmap_invalidWeeks() {
        for (int bad : new int[]{0, -1, 53}) {
            ApiException e = assertThrows(ApiException.class,
                    () -> service().heatmap("u1", bad));
            assertEquals("VALIDATION_ERROR", e.getCode());
        }
    }

    // ------------------------------------------------------------------
    // leaderboard
    // ------------------------------------------------------------------

    private static User user(String id, String nickname, boolean optIn) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        u.setLeaderboardOptIn(optIn);
        return u;
    }

    private static StudySession xpSession(String userId, int correct, int total, LocalDate day) {
        StudySession s = new StudySession();
        s.setUserId(userId);
        s.setCorrectCount(correct);
        s.setTotalItems(total);
        s.setFinishedAt(Date.from(day.atTime(10, 0).atZone(ZONE).toInstant()));
        return s;
    }

    @Test
    void leaderboard_optInOnlyAnonymousAndRanked() {
        GamificationService.WeekRange range = GamificationService.parseWeek("2026-W43", ZONE);
        LocalDate wed = LocalDate.of(2026, 10, 21);
        String me = "64f00000000000000000aa01";
        String other = "64f00000000000000000bb02";
        String hidden = "64f00000000000000000cc03";

        when(userRepository.findByLeaderboardOptInTrue()).thenReturn(List.of(
                user(me, "Hana-chan", true),
                user(other, null, true) // -> nickname mac dinh
        ));
        List<StudySession> sessions = List.of(
                xpSession(me, 10, 20, wed),      // 140 xp
                xpSession(other, 5, 10, wed),    // 70 xp
                xpSession(other, 0, 5, wed),     // 10 xp -> tong 80
                xpSession(hidden, 100, 100, wed) // 1200 xp nhung KHONG opt-in -> loai
        );
        when(studySessionRepository.findByUserIdInAndFinishedAtBetween(any(), any(Date.class), any(Date.class)))
                .thenReturn(sessions);

        GamificationDtos.LeaderboardResponse res = service().leaderboard(me, "2026-W43");

        assertEquals("2026-W43", res.week());
        assertEquals(2, res.totalParticipants());
        assertEquals(2, res.entries().size());
        assertEquals(1, res.entries().get(0).rank());
        assertEquals("Hana-chan", res.entries().get(0).nickname());
        assertEquals(140, res.entries().get(0).xp());
        assertTrue(res.entries().get(0).isMe());
        assertEquals(2, res.entries().get(1).rank());
        assertEquals("Tanu-bb02", res.entries().get(1).nickname());
        assertEquals(80, res.entries().get(1).xp());
        assertFalse(res.entries().get(1).isMe());
        assertEquals(1, res.myRank());
        // privacy: khong co entry nao cua user chua opt-in
        assertTrue(res.entries().stream().noneMatch(e -> e.nickname().contains("cc03")));
    }

    @Test
    void leaderboard_callerNotOptedIn_myRankNull() {
        LocalDate wed = LocalDate.of(2026, 10, 21);
        String me = "64f00000000000000000aa01";
        String other = "64f00000000000000000bb02";
        // caller KHONG co trong danh sach opt-in
        when(userRepository.findByLeaderboardOptInTrue()).thenReturn(List.of(
                user(other, "Tanu", true)));
        when(studySessionRepository.findByUserIdInAndFinishedAtBetween(any(), any(Date.class), any(Date.class)))
                .thenReturn(List.of(
                        xpSession(me, 10, 20, wed),
                        xpSession(other, 5, 10, wed)));

        GamificationDtos.LeaderboardResponse res = service().leaderboard(me, "2026-W43");

        assertNull(res.myRank());
        assertEquals(1, res.entries().size());
        assertTrue(res.entries().stream().noneMatch(GamificationDtos.LeaderboardEntryDto::isMe));
        assertEquals(1, res.totalParticipants());
    }

    @Test
    void leaderboard_top50Limit() {
        LocalDate wed = LocalDate.of(2026, 10, 21);
        List<User> users = new ArrayList<>();
        List<StudySession> sessions = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String id = String.format("64f0000000000000%08d", i);
            users.add(user(id, "N" + i, true));
            sessions.add(xpSession(id, i, 0, wed)); // xp = i*10
        }
        when(userRepository.findByLeaderboardOptInTrue()).thenReturn(users);
        when(studySessionRepository.findByUserIdInAndFinishedAtBetween(any(), any(Date.class), any(Date.class)))
                .thenReturn(sessions);

        GamificationDtos.LeaderboardResponse res =
                service().leaderboard("64f000000000000000000000", "2026-W43");

        assertEquals(50, res.entries().size());
        assertEquals(60, res.totalParticipants());
        assertEquals(1, res.entries().get(0).rank());
        assertEquals(590, res.entries().get(0).xp()); // xp cao nhat
        assertEquals(50, res.entries().get(49).rank());
    }

    @Test
    void leaderboard_invalidWeek() {
        ApiException e = assertThrows(ApiException.class,
                () -> service().leaderboard("u1", "tuan-truoc"));
        assertEquals("VALIDATION_ERROR", e.getCode());
    }

    // ------------------------------------------------------------------
    // updateProfile
    // ------------------------------------------------------------------

    @Test
    void updateProfile_trimsAndSaves() {
        User u = user("u1", null, false);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        GamificationDtos.GamificationProfileResponse res =
                service().updateProfile("u1",
                        new GamificationDtos.GamificationProfileRequest("  Hana-chan  ", true));

        assertEquals("Hana-chan", res.nickname());
        assertTrue(res.leaderboardOptIn());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Hana-chan", captor.getValue().getNickname());
        assertTrue(captor.getValue().isLeaderboardOptIn());
    }

    @Test
    void updateProfile_validation() {
        User u = user("u1", null, false);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service().updateProfile("u1",
                        new GamificationDtos.GamificationProfileRequest("", true))).getCode());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service().updateProfile("u1",
                        new GamificationDtos.GamificationProfileRequest("   ", null))).getCode());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service().updateProfile("u1",
                        new GamificationDtos.GamificationProfileRequest("a".repeat(21), null)))
                .getCode());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service().updateProfile("u1",
                        new GamificationDtos.GamificationProfileRequest("hana@mail.com", null)))
                .getCode());
        // null ca 2 field -> khong doi gi, tra gia tri hien tai
        GamificationDtos.GamificationProfileResponse res = service().updateProfile("u1",
                new GamificationDtos.GamificationProfileRequest(null, null));
        assertEquals("Tanu-u1", res.nickname()); // nickname mac dinh
        assertFalse(res.leaderboardOptIn());
    }

    @Test
    void updateProfile_userNotFound() {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class,
                () -> service().updateProfile("nope",
                        new GamificationDtos.GamificationProfileRequest("X", true)));
        assertEquals("USER_NOT_FOUND", e.getCode());
    }
}
