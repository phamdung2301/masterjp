package com.masterjp.dto;

import java.util.List;

/**
 * Feature: Ngu phap — contract v1 read-only, public GET (theo mau KanjiDtos):
 * - GET /api/grammar?lessonNo=&level= (ca 2 optional; khong param -> tat ca,
 *   sap theo lessonNo roi orderInLesson) -> [GrammarDto]
 * - GET /api/grammar/search?q= -> [GrammarDto] (toi da 50)
 * - GET /api/grammar/{id} -> GrammarDetailDto
 */
public final class GrammarDtos {

    private GrammarDtos() {
    }

    /** List view: danh sach diem ngu phap (danh sach theo bai / ket qua search). */
    public record GrammarDto(String id, int lessonNo, String level,
                             String title, String titleVi, String pattern,
                             String meaningVi, List<String> tags) {
    }

    /** Chi tiet 1 diem ngu phap: day du field cua GrammarDto + noi dung hoc. */
    public record GrammarDetailDto(String id, int lessonNo, String level,
                                   String title, String titleVi, String pattern,
                                   String meaningVi, List<String> tags,
                                   String explanation, String formation,
                                   List<ExampleDto> examples, String notes,
                                   List<String> compareWith) {
    }

    public record ExampleDto(String ja, String kana, String vi) {
    }
}
