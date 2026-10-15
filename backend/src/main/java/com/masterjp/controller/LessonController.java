package com.masterjp.controller;

import com.masterjp.domain.Level;
import com.masterjp.dto.LessonDtos;
import com.masterjp.dto.WordDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.LessonService;
import com.masterjp.util.EnumUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Noi dung public read — GUEST khong can auth (principal co the null). */
@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping
    public List<LessonDtos.LessonDto> list(@RequestParam String level,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        Level lv = EnumUtils.parse(Level.class, level, "level");
        return lessonService.listLessons(lv, principal == null ? null : principal.getId());
    }

    @GetMapping("/{id}")
    public LessonDtos.LessonDetailDto get(@PathVariable String id) {
        return lessonService.getLesson(id);
    }

    @GetMapping("/{id}/words")
    public List<WordDtos.WordDto> words(@PathVariable String id,
                                        @RequestParam(required = false) String section,
                                        @RequestParam(required = false) String cluster,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return lessonService.getWords(id, section, cluster, principal == null ? null : principal.getId());
    }

    /** F-A: cum chu de cua bai (kèm so tu moi cum). Public read-only. */
    @GetMapping("/{id}/clusters")
    public List<LessonDtos.ClusterDto> clusters(@PathVariable String id,
                                                @RequestParam(required = false) String section) {
        return lessonService.getClusters(id, section);
    }
}
