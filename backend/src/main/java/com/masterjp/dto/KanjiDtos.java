package com.masterjp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * F-B (Feature Pack 2) — contract kanji:
 * - GET /api/kanji?lessonNo=&jlpt= (ca 2 optional; khong param -> tat ca,
 *   sap theo so net) -> [{character,onYomi,kunYomi,meaningVi,strokes,jlpt,lessonNos}]
 * - GET /api/kanji/{character} -> chi tiet + words: tu chua chu do
 *   (tai dung WordDto, toi da 50 tu, sap theo lessonNo)
 * - Track 3 (R1-R3 rebuild): POST /api/kanji/quiz
 *   {characters:[...] (1..100), mode: KANJI_MEANING|MEANING_KANJI|KANJI_TYPING}
 *   -> QuizResponse (tai dung shape cua quiz tu vung).
 */
public final class KanjiDtos {

    private KanjiDtos() {
    }

    public record KanjiDto(String character, List<String> onYomi, List<String> kunYomi,
                           String meaningVi, int strokes, String jlpt,
                           List<Integer> lessonNos, String topic) {
    }

    public record KanjiDetailDto(String character, List<String> onYomi, List<String> kunYomi,
                                 String meaningVi, int strokes, String jlpt,
                                 List<Integer> lessonNos, String topic,
                                 List<WordDtos.WordDto> words) {
    }

    /** GET /api/kanji/topics — 1 chu de + so chu trong DB. */
    public record KanjiTopicDto(String code, String label, long count) {
    }

    /**
     * Track 3: quiz kanji tu danh sach chu chon tay.
     * characters rong / qua 100 -> 400 (giong QuickQuizRequest cua quiz tu vung).
     */
    public record KanjiQuizRequest(
            @NotNull(message = "Danh sách chữ kanji không được để trống.")
            @Size(min = 1, max = 100, message = "Chọn từ 1 đến 100 chữ kanji cho mỗi lượt luyện.")
            List<String> characters,
            @NotNull(message = "mode không được để trống.") String mode
    ) {
    }
}
