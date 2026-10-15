package com.masterjp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    /**
     * F-C (Feature Pack 2): luyen nhanh — sinh quiz tu danh sach tu chon tay.
     * POST /api/quiz/quick {wordIds, mode} -> QuizResponse (cung shape voi quiz thuong).
     * KHONG tao CustomSet; FE ghi lich su qua POST /api/sessions nhu binh thuong
     * (scopeType=QUICK, scopeId="quick") va cap nhat SM-2 qua PATCH /api/progress/answer.
     */
    public record QuickQuizRequest(
            @NotNull(message = "Danh sách từ không được để trống.")
            @Size(min = 1, max = 100, message = "Chọn từ 1 đến 100 từ cho mỗi lượt luyện nhanh.")
            List<String> wordIds,
            @NotNull(message = "mode không được để trống.") String mode
    ) {
    }

    public record QuizQuestionDto(String wordId, String promptText, String promptAudioUrl,
                                  List<QuizOptionDto> options, String answerWordId,
                                  String kana, String kanji, String meaningVi) {
    }

    public record QuizOptionDto(String wordId, String text) {
    }
}
