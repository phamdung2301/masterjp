package com.masterjp.controller;

import com.masterjp.dto.ProgressDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.KanjiProgressService;
import com.masterjp.service.ProgressService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final KanjiProgressService kanjiProgressService;

    public ProgressController(ProgressService progressService,
                              KanjiProgressService kanjiProgressService) {
        this.progressService = progressService;
        this.kanjiProgressService = kanjiProgressService;
    }

    /**
     * Cap nhat tien trinh SAU MOI CAU TRA LOI (F-027) — khong batch cuoi phien.
     * SM-2 chay o backend.
     * F-043: mo rong them kind="kanji" + character (tuy chon) — kanji co SM-2
     * rieng (kanji_progress) nhung tra cung shape AnswerResponse.
     */
    @PatchMapping("/answer")
    public ProgressDtos.AnswerResponse answer(@RequestBody ProgressDtos.AnswerRequest req,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        String kind = req.kind() == null ? "vocab" : req.kind().trim().toLowerCase();
        if (!"vocab".equals(kind) && !"kanji".equals(kind)) {
            throw new ApiException("VALIDATION_ERROR",
                    "kind chỉ nhận \"vocab\" hoặc \"kanji\".", HttpStatus.BAD_REQUEST);
        }
        if (req.correct() == null) {
            throw new ApiException("VALIDATION_ERROR",
                    "correct không được để trống.", HttpStatus.BAD_REQUEST);
        }
        if ("kanji".equals(kind)) {
            String character = req.character() == null ? null : req.character().trim();
            if (character == null || character.length() != 1) {
                throw new ApiException("VALIDATION_ERROR",
                        "character phải là 1 ký tự kanji.", HttpStatus.BAD_REQUEST);
            }
            return kanjiProgressService.answerKanji(principal.getId(), character, req.correct());
        }
        if (req.wordId() == null || req.wordId().isBlank()) {
            throw new ApiException("VALIDATION_ERROR",
                    "wordId không được để trống.", HttpStatus.BAD_REQUEST);
        }
        return progressService.answer(principal.getId(), req.wordId(), req.correct());
    }

    /** Tu den han on (F-026): qua han lau xep truoc. */
    @GetMapping("/due")
    public List<ProgressDtos.DueWordDto> due(@RequestParam(required = false) Integer limit,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return progressService.dueWords(principal.getId(), limit);
    }

    /** Dashboard tien trinh (F-028). */
    @GetMapping("/summary")
    public ProgressDtos.ProgressSummaryDto summary(@AuthenticationPrincipal UserPrincipal principal) {
        return progressService.summary(principal.getId());
    }
}
