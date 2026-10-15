package com.masterjp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Contract quiz:
 * - POST /api/quiz/generate {mode: JP_VI|VI_JP|LISTENING|TYPING, scopeType: LESSON|SECTION|SET,
 *   scopeId, limit} -> {questions:[{wordId,promptText,promptAudioUrl,
 *   options:[{wordId,text}],answerWordId,kana,kanji,meaningVi}]}
 * - kana/kanji/meaningVi chỉ có giá trị ở mode TYPING (FE chấm local, không cần
 *   đáp án nhiễu); các mode khác để null và FE bỏ qua (additive, tương thích ngược).
 */
public final class QuizDtos {

    private QuizDtos() {
    }

    public record QuizGenerateRequest(
            @NotNull(message = "mode không được để trống.") String mode,
            @NotNull(message = "scopeType không được để trống.") String scopeType,
            @NotBlank(message = "scopeId không được để trống.") String scopeId,
            Integer limit
    ) {
    }

    public record QuizResponse(List<QuizQuestionDto> questions) {
    }

    public record QuizQuestionDto(String wordId, String promptText, String promptAudioUrl,
                                  List<QuizOptionDto> options, String answerWordId,
                                  String kana, String kanji, String meaningVi) {
    }

    public record QuizOptionDto(String wordId, String text) {
    }
}
