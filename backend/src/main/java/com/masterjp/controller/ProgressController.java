package com.masterjp.controller;

import com.masterjp.dto.ProgressDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.ProgressService;
import jakarta.validation.Valid;
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

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /**
     * Cap nhat tien trinh SAU MOI CAU TRA LOI (F-027) — khong batch cuoi phien.
     * SM-2 chay o backend.
     */
    @PatchMapping("/answer")
    public ProgressDtos.AnswerResponse answer(@Valid @RequestBody ProgressDtos.AnswerRequest req,
                                             @AuthenticationPrincipal UserPrincipal principal) {
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
