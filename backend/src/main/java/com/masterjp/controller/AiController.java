package com.masterjp.controller;

import com.masterjp.dto.AiDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.AiRateLimiter;
import com.masterjp.service.AiService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase B — Sensei AI (F-038/F-039/F-040 + GET /api/ai/quota).
 * Tat ca endpoint can JWT: duoc SecurityConfig bao ve boi
 * {@code .requestMatchers("/api/**").authenticated()} — KHONG them permitAll o day.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final AiRateLimiter rateLimiter;

    public AiController(AiService aiService, AiRateLimiter rateLimiter) {
        this.aiService = aiService;
        this.rateLimiter = rateLimiter;
    }

    /** F-038 — chat hoi-dap ngu phap (stateless; FE giu hoi thoai trong memory). */
    @PostMapping("/chat")
    public AiDtos.ChatResponse chat(@Valid @RequestBody AiDtos.ChatRequest req,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return aiService.chat(principal.getId(), req);
    }

    /** F-039 — cham cau viet (persist vao ai_grades khi thanh cong). */
    @PostMapping("/grade-sentence")
    public AiDtos.GradeSentenceResponse gradeSentence(@Valid @RequestBody AiDtos.GradeSentenceRequest req,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return aiService.gradeSentence(principal.getId(), req);
    }

    /** F-040 — sinh bai tap trac nghiem/dien tu (stateless). */
    @PostMapping("/generate-exercises")
    public AiDtos.GenerateExercisesResponse generateExercises(@Valid @RequestBody AiDtos.GenerateExercisesRequest req,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return aiService.generateExercises(principal.getId(), req);
    }

    /** Tra quota hien tai — KHONG ton quota. */
    @GetMapping("/quota")
    public AiDtos.QuotaResponse quota(@AuthenticationPrincipal UserPrincipal principal) {
        return rateLimiter.readQuota(principal.getId());
    }
}
