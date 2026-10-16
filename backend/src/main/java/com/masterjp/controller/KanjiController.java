package com.masterjp.controller;

import com.masterjp.dto.KanjiDtos;
import com.masterjp.dto.QuizDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.KanjiQuizService;
import com.masterjp.service.KanjiService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F-B: noi dung kanji public read — GUEST khong can auth. */
@RestController
@RequestMapping("/api/kanji")
public class KanjiController {

    private final KanjiService kanjiService;
    private final KanjiQuizService kanjiQuizService;

    public KanjiController(KanjiService kanjiService, KanjiQuizService kanjiQuizService) {
        this.kanjiService = kanjiService;
        this.kanjiQuizService = kanjiQuizService;
    }

    /** Loc theo bai / cap do (ca 2 optional); khong param -> tat ca, sap theo so net. */
    @GetMapping
    public List<KanjiDtos.KanjiDto> list(@RequestParam(required = false) Integer lessonNo,
                                         @RequestParam(required = false) String jlpt) {
        return kanjiService.listKanji(lessonNo, jlpt);
    }

    /** Chi tiet chu + toi da 50 tu chua chu do (character URL-encoded, vd. %E7%A7%81). */
    @GetMapping("/{character}")
    public KanjiDtos.KanjiDetailDto detail(@PathVariable String character) {
        return kanjiService.getDetail(character);
    }

    /**
     * Track 3 (R1-R3 rebuild): quiz kanji tu danh sach chu chon tay.
     * CAN DANG NHAP (giong quiz tu vung, KHONG permitAll): SecurityConfig chi
     * permitAll GET /api/kanji/** nen POST nay roi vao rule "/api/**" ->
     * authenticated(). Response tai dung shape QuizResponse cua quiz thuong.
     * v1: khong luu SM-2 / progress kanji.
     */
    @PostMapping("/quiz")
    public QuizDtos.QuizResponse quiz(@Valid @RequestBody KanjiDtos.KanjiQuizRequest req,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return kanjiQuizService.generateQuiz(req);
    }
}
