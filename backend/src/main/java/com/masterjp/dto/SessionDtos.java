package com.masterjp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;
import java.util.List;

/**
 * Contract session:
 * - POST /api/sessions {mode,scopeType,scopeId,totalItems,correctCount,
 *   items:[{wordId,correct,timeMs}],startedAt,finishedAt} -> {id, streakDays}
 * Backend tu tinh lai totalItems/correctCount tu items va cap nhat streak.
 */
public final class SessionDtos {

    private SessionDtos() {
    }

    public record CreateSessionRequest(
            @NotNull(message = "mode không được để trống.") String mode,
            @NotNull(message = "scopeType không được để trống.") String scopeType,
            @NotBlank(message = "scopeId không được để trống.") String scopeId,
            Integer totalItems,
            Integer correctCount,
            List<SessionItemDto> items,
            Date startedAt,
            Date finishedAt
    ) {
    }

    public record SessionItemDto(String wordId, Boolean correct, Long timeMs) {
    }

    public record SessionResponse(String id, int streakDays) {
    }
}
