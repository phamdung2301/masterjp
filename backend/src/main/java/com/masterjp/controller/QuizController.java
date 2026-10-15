package com.masterjp.controller;

import com.masterjp.dto.QuizDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/generate")
    public QuizDtos.QuizResponse generate(@Valid @RequestBody QuizDtos.QuizGenerateRequest req,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return quizService.generate(principal.getId(), req);
    }

    /** F-C (Feature Pack 2): luyen nhanh tu danh sach tu chon tay. Can dang nhap. */
    @PostMapping("/quick")
    public QuizDtos.QuizResponse quick(@Valid @RequestBody QuizDtos.QuickQuizRequest req,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return quizService.generateQuick(principal.getId(), req);
    }
}
