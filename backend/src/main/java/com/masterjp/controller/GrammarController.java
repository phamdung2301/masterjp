package com.masterjp.controller;

import com.masterjp.dto.GrammarDtos;
import com.masterjp.service.GrammarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Feature: Ngu phap — noi dung public read, V1 read-only — GUEST khong can auth. */
@RestController
@RequestMapping("/api/grammar")
public class GrammarController {

    private final GrammarService grammarService;

    public GrammarController(GrammarService grammarService) {
        this.grammarService = grammarService;
    }

    /** Loc theo bai / cap do; tat ca optional, luon sap lessonNo -> orderInLesson. */
    @GetMapping
    public List<GrammarDtos.GrammarDto> list(@RequestParam(required = false) Integer lessonNo,
                                            @RequestParam(required = false) String level) {
        return grammarService.list(lessonNo, level);
    }

    /** Tim kiem theo nghia Viet (khong dau van ra) hoac mau Nhat (title); toi da 50. */
    @GetMapping("/search")
    public List<GrammarDtos.GrammarDto> search(@RequestParam(required = false) String q) {
        return grammarService.search(q);
    }

    /** Chi tiet 1 diem ngu phap, vd. /api/grammar/g01-01 */
    @GetMapping("/{id}")
    public GrammarDtos.GrammarDetailDto detail(@PathVariable String id) {
        return grammarService.detail(id);
    }
}
