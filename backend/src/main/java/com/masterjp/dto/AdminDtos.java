package com.masterjp.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Contract admin:
 * - GET /api/admin/users?q=&page= ; PATCH /api/admin/users/{id}/plan {plan: FREE|PREMIUM, expiresAt?}
 * - GET /api/admin/stats -> {totalUsers,totalWords,totalLessons,sessionsToday,sessionsByDay,topWrongWords}
 * - GET /api/admin/audit-logs?page=
 * - POST /api/admin/audio/regenerate {wordIds:[]} -> {jobId,status}
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserDto(String id, String email, String name, String role, String plan,
                               Date planExpiresAt, int streakDays, Date createdAt) {
    }

    public record UpdatePlanRequest(
            @NotNull(message = "plan không được để trống.") String plan, // FREE | PREMIUM
            Date expiresAt
    ) {
    }

    public record StatsResponse(long totalUsers, long totalWords, long totalLessons,
                                long totalSessions,
                                long sessionsToday, List<DayCountDto> sessionsByDay,
                                List<TopWrongWordDto> topWrongWords) {
    }

    public record DayCountDto(String date, long count) {
    }

    public record TopWrongWordDto(String wordId, String kana, String kanji, String romaji,
                                  String meaningVi, int lessonNo, long wrongCount) {
    }

    public record AuditLogDto(String id, String adminId, String adminEmail, String action,
                              String targetType, String targetId, Map<String, Object> diff,
                              Date createdAt) {
    }

    public record AudioJobResponse(String jobId, String status) {
    }

    /**
     * Contract job audio (F-033): {jobId, status, total, done, failed}.
     * status: QUEUED | RUNNING | DONE | FAILED.
     */
    public record AudioJobDetailDto(String jobId, String status, int total, int done,
                                    int failed) {
    }
}
