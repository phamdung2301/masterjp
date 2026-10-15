package com.masterjp.dto;

import java.util.List;

/**
 * F-B (Feature Pack 2) — contract kanji:
 * - GET /api/kanji?lessonNo=&jlpt= (ca 2 optional; khong param -> tat ca,
 *   sap theo so net) -> [{character,onYomi,kunYomi,meaningVi,strokes,jlpt,lessonNos}]
 * - GET /api/kanji/{character} -> chi tiet + words: tu chua chu do
 *   (tai dung WordDto, toi da 50 tu, sap theo lessonNo)
 */
public final class KanjiDtos {

    private KanjiDtos() {
    }

    public record KanjiDto(String character, List<String> onYomi, List<String> kunYomi,
                           String meaningVi, int strokes, String jlpt,
                           List<Integer> lessonNos) {
    }

    public record KanjiDetailDto(String character, List<String> onYomi, List<String> kunYomi,
                                 String meaningVi, int strokes, String jlpt,
                                 List<Integer> lessonNos, List<WordDtos.WordDto> words) {
    }
}
