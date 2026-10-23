package com.masterjp.controller;

import com.masterjp.dto.ReviewDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.ReviewQueueService;
import com.masterjp.service.WrongAnswerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F-043 — Review queue thong nhat (SM-2 tu vung + kanji + cau sai).
 * Tat ca endpoint can JWT (duoc SecurityConfig bao ve boi
 * {@code .requestMatchers("/api/**").authenticated()}).
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewQueueService reviewQueueService;
    private final WrongAnswerService wrongAnswerService;

    public ReviewController(ReviewQueueService reviewQueueService,
                            WrongAnswerService wrongAnswerService) {
        this.reviewQueueService = reviewQueueService;
        this.wrongAnswerService = wrongAnswerService;
    }

    /**
     * Hang doi on tap tron 3 nguon: wrong (moi nhat truoc) -> vocab/kanji
     * den han (overdueDays giam dan). Queue khong persist — aggregate luc request.
     */
    @GetMapping("/queue")
    public ReviewDtos.QueueResponse queue(@RequestParam(required = false) Integer limit,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return reviewQueueService.queue(principal.getId(), limit);
    }

    /**
     * Resolve 1 cau sai sau khi user tra loi dung trong phien on tap.
     * Chi nhan {"resolved": true}; idempotent; id khong thuoc user -> 404.
     */
    @PatchMapping("/wrong/{id}")
    public ReviewDtos.ResolveWrongResponse resolveWrong(@PathVariable String id,
                                                        @RequestBody(required = false)
                                                        ReviewDtos.ResolveWrongRequest req,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        if (req == null || !Boolean.TRUE.equals(req.resolved())) {
            throw new ApiException("VALIDATION_ERROR",
                    "Yêu cầu không hợp lệ. Vui lòng thử lại.", HttpStatus.BAD_REQUEST);
        }
        return wrongAnswerService.resolve(principal.getId(), id);
    }
}
