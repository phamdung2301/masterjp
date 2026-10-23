package com.masterjp.controller;

import com.masterjp.dto.GamificationDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.GamificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase C — F-042 Gamification (huy hieu · heatmap · bang xep hang tuan).
 * Tat ca endpoint can JWT: duoc SecurityConfig bao ve boi
 * {@code .requestMatchers("/api/**").authenticated()} — KHONG them permitAll o day.
 */
@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    /** Ca 12 huy hieu + trang thai da dat/chua dat (thay badge tinh FE Phase A). */
    @GetMapping("/badges")
    public GamificationDtos.BadgesResponse badges(
            @AuthenticationPrincipal UserPrincipal principal) {
        return gamificationService.badges(principal.getId());
    }

    /** Heatmap weeks tuan gan nhat; weeks=1..52, mac dinh 12. */
    @GetMapping("/heatmap")
    public GamificationDtos.HeatmapResponse heatmap(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "weeks", required = false) Integer weeks) {
        return gamificationService.heatmap(principal.getId(), weeks == null ? 12 : weeks);
    }

    /**
     * Bang xep hang tuan ISO (week=YYYY-Www, mac dinh tuan hien tai).
     * Chi user opt-in moi xuat hien; an danh tuyet doi (khong email/userId).
     */
    @GetMapping("/leaderboard")
    public GamificationDtos.LeaderboardResponse leaderboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "week", required = false) String week) {
        return gamificationService.leaderboard(principal.getId(), week);
    }

    /** Doi nickname (1-20 ky tu) / bat-tat hien thi tren bang xep hang. */
    @PatchMapping("/profile")
    public GamificationDtos.GamificationProfileResponse updateProfile(
            @RequestBody GamificationDtos.GamificationProfileRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return gamificationService.updateProfile(principal.getId(), req);
    }
}
