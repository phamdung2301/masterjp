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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Phase C — F-042 Gamification: huy hieu (doc), heatmap, bang xep hang tuan, profile.
 * <p>
 * Aggregation chay bang query don gian + gom nhom trong Java (khong dung toan tu
 * date aggregation phuc tap cua Mongo) de de unit test bang Mockito.
 */
@Service
public class GamificationService {

    private static final Pattern WEEK_PATTERN = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    private static final DateTimeFormatter ISO_OFFSET_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final int LEADERBOARD_LIMIT = 50; // hard-code theo spec

    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final AppTime appTime;

    public GamificationService(UserBadgeRepository userBadgeRepository,
                              UserRepository userRepository,
                              StudySessionRepository studySessionRepository,
                              AppTime appTime) {
        this.userBadgeRepository = userBadgeRepository;
        this.userRepository = userRepository;
        this.studySessionRepository = studySessionRepository;
        this.appTime = appTime;
    }

    /** GET /api/gamification/badges — ca 12 badge + trang thai da dat/chua dat. */
    public GamificationDtos.BadgesResponse badges(String userId) {
        Map<String, Date> earnedAtByCode = userBadgeRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserBadge::getBadgeCode, UserBadge::getEarnedAt, (a, b) -> a));
        List<GamificationDtos.BadgeDto> badges = BadgeCatalog.ALL.stream()
                .map(b -> new GamificationDtos.BadgeDto(
                        b.code(), b.icon(), b.name(), b.description(),
                        earnedAtByCode.containsKey(b.code()),
                        earnedAtByCode.get(b.code())))
                .toList();
        return new GamificationDtos.BadgesResponse(badges, earnedAtByCode.size(), BadgeCatalog.ALL.size());
    }

    /** GET /api/gamification/heatmap?weeks=12 — ngay khong hoc -> minutes: 0. */
    public GamificationDtos.HeatmapResponse heatmap(String userId, int weeks) {
        if (weeks < 1 || weeks > 52) {
            throw new ApiException("VALIDATION_ERROR",
                    "Số tuần phải từ 1 đến 52.", HttpStatus.BAD_REQUEST);
        }
        LocalDate today = appTime.today();
        LocalDate start = today.minusDays(weeks * 7L - 1);
        List<StudySession> sessions =
                studySessionRepository.findByUserIdAndFinishedAtGreaterThanEqual(
                        userId, appTime.startOfDay(start));

        Map<String, DayAcc> acc = new HashMap<>();
        for (StudySession s : sessions) {
            if (s.getFinishedAt() == null) {
                continue;
            }
            LocalDate d = appTime.toLocalDate(s.getFinishedAt());
            if (d.isBefore(start) || d.isAfter(today)) {
                continue;
            }
            long ms = durationMs(s);
            acc.computeIfAbsent(d.toString(), k -> new DayAcc()).add(ms);
        }

        List<GamificationDtos.HeatmapDayDto> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            DayAcc a = acc.get(d.toString());
            int minutes = a == null ? 0 : (int) Math.round(a.totalMs / 60000.0);
            int sessionCount = a == null ? 0 : a.sessions;
            days.add(new GamificationDtos.HeatmapDayDto(d.toString(), minutes, sessionCount));
        }
        return new GamificationDtos.HeatmapResponse(weeks, days);
    }

    private static long durationMs(StudySession s) {
        if (s.getStartedAt() == null || s.getFinishedAt() == null) {
            return 0;
        }
        long ms = s.getFinishedAt().getTime() - s.getStartedAt().getTime();
        return Math.max(0, ms);
    }

    private static class DayAcc {
        long totalMs;
        int sessions;

        void add(long ms) {
            totalMs += ms;
            sessions++;
        }
    }

    /**
     * GET /api/gamification/leaderboard?week=YYYY-Www — top 50 theo XP, opt-in only,
     * an danh tuyet doi (chi nickname + isMe, KHONG email/userId).
     * Cong thuc XP (binding): XP_session = correctCount * 10 + totalItems * 2.
     */
    public GamificationDtos.LeaderboardResponse leaderboard(String callerId, String weekParam) {
        WeekRange range = parseWeek(weekParam, appTime.zoneId());

        Map<String, String> nicknameById = new HashMap<>();
        for (User u : userRepository.findByLeaderboardOptInTrue()) {
            nicknameById.put(u.getId(), effectiveNickname(u));
        }
        boolean callerOptedIn = nicknameById.containsKey(callerId);

        Map<String, EntryAcc> acc = new HashMap<>();
        if (!nicknameById.isEmpty()) {
            // M1 fix: chi quet session cua opted-in users (khong phai toan bo users).
            List<StudySession> sessions = studySessionRepository.findByUserIdInAndFinishedAtBetween(
                    nicknameById.keySet(), range.start(), range.end());
            for (StudySession s : sessions) {
                String uid = s.getUserId();
                if (uid == null || !nicknameById.containsKey(uid)) {
                    continue;
                }
                acc.computeIfAbsent(uid, k -> new EntryAcc()).add(xpFor(s.getCorrectCount(), s.getTotalItems()));
            }
        }

        List<Map.Entry<String, EntryAcc>> sorted = acc.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, EntryAcc> e) -> e.getValue().xp).reversed()
                        .thenComparingInt(e -> -e.getValue().sessions)
                        .thenComparing(e -> nicknameById.get(e.getKey())))
                .toList();

        List<GamificationDtos.LeaderboardEntryDto> entries = new ArrayList<>();
        Integer myRank = null;
        for (int i = 0; i < sorted.size(); i++) {
            String uid = sorted.get(i).getKey();
            EntryAcc a = sorted.get(i).getValue();
            int rank = i + 1;
            if (uid.equals(callerId) && callerOptedIn) {
                myRank = rank;
            }
            if (i < LEADERBOARD_LIMIT) {
                entries.add(new GamificationDtos.LeaderboardEntryDto(
                        rank, nicknameById.get(uid), a.xp, a.sessions, uid.equals(callerId)));
            }
        }
        if (!callerOptedIn) {
            myRank = null; // chua opt-in: duoc xem bang nhung khong co hang
        }
        return new GamificationDtos.LeaderboardResponse(
                range.week(), range.weekStartIso(), range.weekEndIso(),
                entries, myRank, acc.size());
    }

    private static class EntryAcc {
        int xp;
        int sessions;

        void add(int xp) {
            this.xp += xp;
            this.sessions++;
        }
    }

    /** PATCH /api/gamification/profile — doi nickname / opt-in, co hieu luc ngay. */
    public GamificationDtos.GamificationProfileResponse updateProfile(
            String userId, GamificationDtos.GamificationProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
        if (req.nickname() != null) {
            // L2 fix (security audit): strip Unicode control/format chars (vd. U+202E
            // RTL override) truoc khi validate — chong gia mao nickname tren bang cong khai.
            String nick = req.nickname().replaceAll("\\p{C}", "").trim();
            if (nick.length() < 1 || nick.length() > 20) {
                throw new ApiException("VALIDATION_ERROR",
                        "Biệt danh phải từ 1 đến 20 ký tự.", HttpStatus.BAD_REQUEST);
            }
            if (nick.contains("@")) {
                throw new ApiException("VALIDATION_ERROR",
                        "Biệt danh không được chứa ký tự @ — đừng dùng email làm biệt danh.",
                        HttpStatus.BAD_REQUEST);
            }
            user.setNickname(nick);
        }
        if (req.leaderboardOptIn() != null) {
            user.setLeaderboardOptIn(req.leaderboardOptIn());
        }
        userRepository.save(user);
        return new GamificationDtos.GamificationProfileResponse(
                effectiveNickname(user), user.isLeaderboardOptIn());
    }

    // ------------------------------------------------------------------
    // Helper thuan tuy (package-private de unit test)
    // ------------------------------------------------------------------

    /** XP cua 1 session — cong thuc binding DOC-12 muc 4.5. */
    static int xpFor(int correctCount, int totalItems) {
        return correctCount * 10 + totalItems * 2;
    }

    /**
     * Biet danh hieu dung: nickname tu dat, neu chua co thi mac dinh
     * "Tanu-" + 4 ky tu cuoi userId.
     */
    static String effectiveNickname(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        String id = user.getId() == null ? "" : user.getId();
        String tail = id.length() >= 4 ? id.substring(id.length() - 4) : id;
        return "Tanu-" + tail;
    }

    record WeekRange(String week, Date start, Date end, String weekStartIso, String weekEndIso) {
    }

    /**
     * Parse param week "YYYY-Www" (ISO week) hoac null/blank -> tuan hien tai.
     * Sai format hoac tuan khong ton tai (vd. W53 cua nam chi co 52 tuan) -> 400.
     * Tuan tinh theo Asia/Ho_Chi_Minh: Thu Hai 00:00 -> Chu Nhat 23:59:59.
     */
    static WeekRange parseWeek(String weekParam, ZoneId zone) {
        String weekStr;
        LocalDate monday;
        if (weekParam == null || weekParam.isBlank()) {
            monday = LocalDate.now(zone).with(WeekFields.ISO.dayOfWeek(), 1);
            weekStr = String.format("%d-W%02d",
                    monday.get(WeekFields.ISO.weekBasedYear()),
                    monday.get(WeekFields.ISO.weekOfYear()));
        } else {
            weekStr = weekParam.trim();
            Matcher m = WEEK_PATTERN.matcher(weekStr);
            if (!m.matches()) {
                throw invalidWeek();
            }
            int year = Integer.parseInt(m.group(1));
            int week = Integer.parseInt(m.group(2));
            if (week < 1 || week > 53) {
                throw invalidWeek();
            }
            monday = LocalDate.of(year, 1, 4)
                    .with(WeekFields.ISO.weekOfYear(), week)
                    .with(WeekFields.ISO.dayOfWeek(), 1);
            if (monday.get(WeekFields.ISO.weekBasedYear()) != year
                    || monday.get(WeekFields.ISO.weekOfYear()) != week) {
                throw invalidWeek(); // vd. W53 cua nam khong co tuan 53
            }
        }
        LocalDate sunday = monday.plusDays(6);
        ZonedDateTime startZdt = monday.atStartOfDay(zone);
        ZonedDateTime endZdt = sunday.atTime(23, 59, 59).atZone(zone);
        return new WeekRange(
                weekStr,
                Date.from(startZdt.toInstant()),
                Date.from(endZdt.toInstant()),
                ISO_OFFSET_FMT.format(startZdt),
                ISO_OFFSET_FMT.format(endZdt));
    }

    private static ApiException invalidWeek() {
        return new ApiException("VALIDATION_ERROR",
                "Định dạng tuần không hợp lệ.", HttpStatus.BAD_REQUEST);
    }
}
