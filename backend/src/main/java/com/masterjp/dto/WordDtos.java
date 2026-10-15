package com.masterjp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Date;
import java.util.List;

/**
 * Contract tu vung:
 * - GET /api/lessons/{id}/words?section= -> [{id,lessonId,section,kana,kanji,romaji,
 *   meaningVi,pos,exampleJa,exampleVi,audioUrl,orderInLesson,studyStatus}]
 *   (studyStatus null neu guest)
 * - GET /api/words/search?q= -> [{...word, lessonNo, lessonId}]
 */
public final class WordDtos {

    private WordDtos() {
    }

    public record WordDto(String id, String lessonId, String section, String kana, String kanji,
                          String romaji, String meaningVi, String pos, String exampleJa,
                          String exampleVi, String audioUrl, int orderInLesson,
                          String studyStatus, String cluster) {
    }

    public record WordSearchDto(String id, String lessonId, int lessonNo, String section,
                                String kana, String kanji, String romaji, String meaningVi,
                                String pos, String exampleJa, String exampleVi,
                                String audioUrl, int orderInLesson) {
    }

    public record CreateWordRequest(
            @NotBlank(message = "lessonId không được để trống.") String lessonId,
            @NotNull(message = "section không được để trống.") String section, // VOCAB|REFERENCE|PHRASE
            @NotBlank(message = "Kana không được để trống.") String kana,
            String kanji,
            @NotBlank(message = "Romaji không được để trống.") String romaji,
            @NotBlank(message = "Nghĩa tiếng Việt không được để trống.") String meaningVi,
            String pos,
            String exampleJa,
            String exampleVi,
            int orderInLesson,
            String cluster // F-A: cum chu de (optional, mac dinh "khac")
    ) {
    }

    public record UpdateWordRequest(
            String section,
            @Size(max = 100, message = "Kana quá dài.") String kana,
            String kanji,
            String romaji,
            String meaningVi,
            String pos,
            String exampleJa,
            String exampleVi,
            Integer orderInLesson,
            String cluster // F-A: admin sua cum chu de cua tu (vd. "do-dung", "khac")
    ) {
    }

    /** 1 dong du lieu import Excel (FR-032). */
    public record WordRowDto(Integer lessonNo, String section, String kana, String kanji,
                             String romaji, String meaningVi, String pos,
                             String exampleJa, String exampleVi) {
    }

    /**
     * Buoc 1 tra ve: bao cao validate + danh sach cac dong HOP LE de FE giu va
     * gui lai o buoc confirm (khong can importToken phia server).
     */
    public record ImportValidateResponse(int totalRows, int validRows, List<RowError> errors,
                                         List<WordRowDto> rows) {
    }

    public record RowError(int row, String message) {
    }

    public record ImportConfirmRequest(
            @NotNull(message = "Danh sách dòng import không được để trống.")
            @Size(min = 1, message = "Danh sách dòng import không được để trống.") List<WordRowDto> rows
    ) {
    }

    public record ImportConfirmResponse(int imported, int skipped) {
    }

    public record RegenerateAudioRequest(
            @NotNull(message = "Danh sách từ không được để trống.")
            @Size(min = 1, message = "Danh sách từ không được để trống.") List<String> wordIds
    ) {
    }

    /** Admin xem danh sach tu (kem ca tu da xoa mem). */
    public record AdminWordDto(String id, String lessonId, int lessonNo, String section,
                               String kana, String kanji, String romaji, String meaningVi,
                               String pos, String exampleJa, String exampleVi,
                               String audioUrl, int orderInLesson, boolean active,
                               Date updatedAt, String cluster) {
    }
}
