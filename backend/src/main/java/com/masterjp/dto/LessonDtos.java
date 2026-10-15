package com.masterjp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contract: GET /api/lessons?level=N5|N4 -> [{id,level,lessonNo,titleJa,titleVi,order,totalWords,masteredWords}] */
public final class LessonDtos {

    private LessonDtos() {
    }

    public record LessonDto(String id, String level, int lessonNo, String titleJa, String titleVi,
                            int order, long totalWords, long masteredWords) {
    }

    public record LessonDetailDto(String id, String level, int lessonNo, String titleJa,
                                  String titleVi, int order) {
    }

    public record CreateLessonRequest(
            @NotNull(message = "level không được để trống.") String level, // N5 | N4
            @Min(value = 1, message = "lessonNo phải từ 1 đến 50.")
            @Max(value = 50, message = "lessonNo phải từ 1 đến 50.") int lessonNo,
            @NotBlank(message = "Tiêu đề tiếng Nhật không được để trống.") String titleJa,
            @NotBlank(message = "Tiêu đề tiếng Việt không được để trống.") String titleVi,
            int order
    ) {
    }

    public record UpdateLessonRequest(
            String level,
            Integer lessonNo,
            @Size(max = 200, message = "Tiêu đề quá dài.") String titleJa,
            @Size(max = 200, message = "Tiêu đề quá dài.") String titleVi,
            Integer order
    ) {
    }
}
