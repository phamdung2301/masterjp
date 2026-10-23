package com.masterjp.dto;

import java.util.Date;
import java.util.List;

/**
 * Contract gamification (F-042):
 * - GET /api/gamification/badges -> {badges, earnedCount, totalCount}
 * - GET /api/gamification/heatmap?weeks=12 -> {weeks, days:[{date, minutes, sessions}]}
 * - GET /api/gamification/leaderboard?week=YYYY-Www ->
 *   {week, weekStart, weekEnd, entries:[{rank, nickname, xp, sessions, isMe}], myRank, totalParticipants}
 * - PATCH /api/gamification/profile {nickname?, leaderboardOptIn?} -> {nickname, leaderboardOptIn}
 *
 * Privacy binding: leaderboard KHONG BAO GIO tra email / ten that / userId.
 */
public final class GamificationDtos {

    private GamificationDtos() {
    }

    public record BadgeDto(
            String code,
            String icon,
            String name,
            String description,
            boolean earned,
            Date earnedAt
    ) {
    }

    public record BadgesResponse(
            List<BadgeDto> badges,
            int earnedCount,
            int totalCount
    ) {
    }

    public record HeatmapDayDto(
            String date, // yyyy-MM-dd
            int minutes,
            int sessions
    ) {
    }

    public record HeatmapResponse(
            int weeks,
            List<HeatmapDayDto> days // du weeks*7 ngay lien tiep, ket thuc hom nay
    ) {
    }

    public record LeaderboardEntryDto(
            int rank,
            String nickname,
            int xp,
            int sessions,
            boolean isMe
    ) {
    }

    public record LeaderboardResponse(
            String week, // YYYY-Www
            String weekStart, // vd. "2026-10-19T00:00:00+07:00"
            String weekEnd, // vd. "2026-10-25T23:59:59+07:00"
            List<LeaderboardEntryDto> entries, // toi da 50
            Integer myRank, // null neu caller chua opt-in hoac tuan nay chua hoc
            int totalParticipants
    ) {
    }

    public record GamificationProfileRequest(
            String nickname, // tuy chon; sau trim(): 1-20 ky tu
            Boolean leaderboardOptIn // tuy chon
    ) {
    }

    public record GamificationProfileResponse(
            String nickname,
            boolean leaderboardOptIn
    ) {
    }
}
