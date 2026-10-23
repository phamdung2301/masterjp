package com.masterjp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.List;

/**
 * Contract F-043 (Review queue thong nhat):
 * - GET /api/review/queue?limit= -> {items, counts, estimatedMinutes}
 * - PATCH /api/review/wrong/{id} {resolved:true} -> {id, resolved:true}
 *
 * Item co 3 dang (field names CHINH XAC theo spec 5.5):
 * - kind="wrong", refKind="vocab": {kind,id,refKind,wordId,kana,kanji,romaji,meaningVi,audioUrl,createdAt}
 * - kind="wrong", refKind="ai_sentence": {kind,id,refKind,sentence,corrected,errors:[{span,issue,fix}]}
 * - kind="vocab": {kind,wordId,kana,kanji,romaji,meaningVi,audioUrl,overdueDays}
 * - kind="kanji": {kind,character,meaningVi,onYomi,kunYomi,overdueDays}
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /** estimatedMinutes = ceil((wrong*15 + vocab*8 + kanji*8) / 60) theo counts CHUA cat limit. */
    public record QueueResponse(List<QueueItemDto> items, QueueCountsDto counts,
                                int estimatedMinutes) {
    }

    public record QueueCountsDto(int vocab, int kanji, int wrong, int total) {
    }

    /** @JsonInclude NON_NULL: moi kind chi serialize field cua minh. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueueItemDto(
            String kind,
            // wrong
            String id,
            String refKind,
            // wrong/vocab
            String wordId,
            String kana,
            String kanji,
            String romaji,
            String meaningVi,
            String audioUrl,
            // wrong/vocab
            Date createdAt,
            // vocab/kanji
            Long overdueDays,
            // kanji
            String character,
            List<String> onYomi,
            List<String> kunYomi,
            // wrong + refKind=ai_sentence
            String sentence,
            String corrected,
            List<AiErrorDto> errors
    ) {
    }

    public record AiErrorDto(String span, String issue, String fix) {
    }

    public record ResolveWrongRequest(Boolean resolved) {
    }

    public record ResolveWrongResponse(String id, boolean resolved) {
    }
}
