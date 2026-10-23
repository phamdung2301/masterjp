package com.masterjp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Contract tien trinh:
 * - PATCH /api/progress/answer {wordId,correct,timeMs}
 *   -> {status,nextReviewAt,easeFactor,intervalDays,repetitions}
 * - PATCH /api/progress/answer {kind:"kanji",character,correct,timeMs} (F-043)
 *   -> cung shape AnswerResponse, ghi vao kanji_progress
 * - GET /api/progress/due?limit=50 -> [{word..., overdueDays}]
 * - GET /api/progress/summary -> {byLevel:{N5:{total,new,learning,mastered},N4:{...}},
 *   lessons:[{lessonId,lessonNo,level,titleJa,titleVi,total,mastered}], streakDays, recentSessions:[...]}
 */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    /**
     * F-043: them kind + character (tuy chon).
     * - kind null/"vocab" -> wordId bat buoc (nhu cu).
     * - kind "kanji" -> character bat buoc (dung 1 ky tu), wordId khong can.
     * Validate dieu kien o ProgressController (400 VALIDATION_ERROR).
     */
    public record AnswerRequest(
            String wordId,
            Boolean correct,
            Long timeMs,
            String kind,
            String character
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
