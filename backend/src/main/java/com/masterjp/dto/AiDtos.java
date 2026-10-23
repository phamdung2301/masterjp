package com.masterjp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Phase B — Sensei AI contracts.
 * - POST /api/ai/chat {message, lessonNo?, grammarPointId?} -> {reply, usage}
 * - POST /api/ai/grade-sentence {sentence, grammarPointId} -> {score, corrected, errors, explanation}
 * - POST /api/ai/generate-exercises {grammarPointId, count?} -> {exercises}
 * - GET /api/ai/quota -> {limit, used, remaining, resetAt}
 */
public final class AiDtos {

    private AiDtos() {
    }

    // ---------- F-038 chat ----------

    public record ChatRequest(
            @NotBlank(message = "Câu hỏi không được để trống.")
            @Size(max = 2000, message = "Câu hỏi tối đa 2000 ký tự.")
            String message,
            /** 1..50; neu grammarPointId co gia tri thi field nay bi bo qua. Null = khong co context bai hoc. */
            @Min(value = 1, message = "lessonNo phải từ 1 đến 50.")
            @Max(value = 50, message = "lessonNo phải từ 1 đến 50.")
            Integer lessonNo,
            String grammarPointId
    ) {
    }

    public record ChatResponse(String reply, UsageDto usage) {
    }

    /** Provider thuc su da sinh cau tra loi: "groq" | "openrouter". */
    public record UsageDto(String provider) {
    }

    // ---------- F-039 grade sentence ----------

    public record GradeSentenceRequest(
            @NotBlank(message = "Câu cần chấm không được để trống.")
            @Size(max = 500, message = "Chỉ chấm 1 câu, tối đa 500 ký tự.")
            String sentence,
            @NotBlank(message = "grammarPointId không được để trống.")
            String grammarPointId
    ) {
    }

    public record GradeSentenceResponse(
            int score,
            String corrected,
            List<ErrorItemDto> errors,
            String explanation
    ) {
    }

    public record ErrorItemDto(String span, String issue, String fix) {
    }

    // ---------- F-040 generate exercises ----------

    public record GenerateExercisesRequest(
            @NotBlank(message = "grammarPointId không được để trống.")
            String grammarPointId,
            /** Chi nhan 5 hoac 10; null -> mac dinh 5 (validate trong service). */
            Integer count
    ) {
    }

    public record GenerateExercisesResponse(List<ExerciseDto> exercises) {
    }

    public record ExerciseDto(
            /** "choice" | "fill". */
            String type,
            String question,
            /** type=choice: dung 4 string phan biet; type=fill: null. */
            List<String> choices,
            String answer,
            String explanation
    ) {
    }

    // ---------- Quota ----------

    public record QuotaResponse(int limit, int used, int remaining, String resetAt) {
    }

    /** Shape 429 mo rong: {code, message} + resetAt (khong nhet vao ErrorResponse chung). */
    public record QuotaExceededResponse(String code, String message, String resetAt) {
    }
}
