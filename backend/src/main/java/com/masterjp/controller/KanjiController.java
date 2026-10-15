package com.masterjp.controller;

import com.masterjp.dto.KanjiDtos;
import com.masterjp.service.KanjiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F-B: noi dung kanji public read — GUEST khong can auth. */
@RestController
@RequestMapping("/api/kanji")
public class KanjiController {

    private final KanjiService kanjiService;

    public KanjiController(KanjiService kanjiService) {
        this.kanjiService = kanjiService;
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
}
