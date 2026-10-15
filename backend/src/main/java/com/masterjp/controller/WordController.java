package com.masterjp.controller;

import com.masterjp.dto.WordDtos;
import com.masterjp.service.WordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tim kiem tu dien public (F-012). */
@RestController
@RequestMapping("/api/words")
public class WordController {

    private final WordService wordService;

    public WordController(WordService wordService) {
        this.wordService = wordService;
    }

    @GetMapping("/search")
    public List<WordDtos.WordSearchDto> search(@RequestParam(defaultValue = "") String q) {
        return wordService.search(q);
    }
}
