package com.masterjp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Contract tien trinh:
 * - PATCH /api/progress/answer {wordId,correct,timeMs}
 *   -> {status,nextReviewAt,easeFactor,intervalDays,repetitions}
 * - GET /api/progress/due?limit=50 -> [{word..., overdueDays}]
 * - GET /api/progress/summary -> {byLevel:{N5:{total,new,learning,mastered},N4:{...}},
 *   lessons:[{lessonId,lessonNo,level,titleJa,titleVi,total,mastered}], streakDays, recentSessions:[...]}
 */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    public record AnswerRequest(
            @NotBlank(message = "wordId không được để trống.") String wordId,
            @NotNull(message = "correct không được để trống.") Boolean correct,
            Long timeMs
    ) {
    }

    public record AnswerResponse(String status, Date nextReviewAt, double easeFactor,
                                 int intervalDays, int repetitions) {
    }

    public record DueWordDto(String id, String lessonId, int lessonNo, String section,
                             String kana, String kanji, String romaji, String meaningVi,
                             String pos, String exampleJa, String exampleVi,
                             String audioUrl, int orderInLesson, String status,
                             long overdueDays) {
    }

    public record ProgressSummaryDto(Map<String, LevelProgressDto> byLevel,
                                     List<LessonProgressDto> lessons,
                                     int streakDays,
                                     List<RecentSessionDto> recentSessions) {
    }

    public record LevelProgressDto(long total,
                                   @JsonProperty("new") long newCount,
                                   long learning, long mastered) {
    }

    public record LessonProgressDto(String lessonId, int lessonNo, String level,
                                    String titleJa, String titleVi,
                                    long total, long mastered) {
    }

    public record RecentSessionDto(String id, String mode, String scopeType, int totalItems,
                                   int correctCount, Date startedAt, Date finishedAt) {
    }
}
