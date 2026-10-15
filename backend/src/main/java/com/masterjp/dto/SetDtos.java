package com.masterjp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Date;
import java.util.List;

/**
 * Contract bo the (F-024/F-025/F-035):
 * - GET /api/sets -> [{id,name,description,wordCount,isPublic,createdAt,updatedAt}]
 * - GET /api/sets/{id} -> {.., words:[...]} (owner hoac public)
 * - POST /api/sets/{id}/words {wordIds:[]} -> {added,skipped}
 * - PATCH /api/sets/{id}/share {isPublic} -> 403 "Nâng cấp gói để chia sẻ công khai"
 *   neu plan/role khong nam trong SHARING_ALLOWED_PLANS
 * - GET /api/sets/public?q=&page= (khong can auth) -> {items:[...],total}
 * - POST /api/sets/{id}/fork (auth) -> {id,name}
 */
public final class SetDtos {

    private SetDtos() {
    }

    public record CreateSetRequest(
            @NotBlank(message = "Tên bộ thẻ không được để trống.")
            @Size(max = 100, message = "Tên bộ thẻ tối đa 100 ký tự.") String name,
            @Size(max = 500, message = "Mô tả tối đa 500 ký tự.") String description
    ) {
    }

    public record UpdateSetRequest(
            @Size(max = 100, message = "Tên bộ thẻ tối đa 100 ký tự.") String name,
            @Size(max = 500, message = "Mô tả tối đa 500 ký tự.") String description
    ) {
    }

    public record AddWordsRequest(
            @NotNull(message = "Danh sách từ không được để trống.")
            @Size(min = 1, message = "Danh sách từ không được để trống.") List<String> wordIds
    ) {
    }

    public record ShareSetRequest(
            @NotNull(message = "isPublic không được để trống.") Boolean isPublic
    ) {
    }

    public record SetDto(String id, String name, String description, int wordCount,
                         boolean isPublic, Date createdAt, Date updatedAt) {
    }

    public record SetDetailDto(String id, String name, String description, int wordCount,
                               boolean isPublic, Date createdAt, Date updatedAt,
                               List<WordDtos.WordDto> words) {
    }

    public record AddWordsResponse(int added, int skipped) {
    }

    public record PublicSetDto(String id, String name, String description, int wordCount,
                               String authorName, int forkCount, Date updatedAt) {
    }

    public record PublicSetsResponse(List<PublicSetDto> items, long total) {
    }

    public record ForkResponse(String id, String name) {
    }
}
