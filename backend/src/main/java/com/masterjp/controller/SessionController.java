package com.masterjp.controller;

import com.masterjp.dto.SessionDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Ket thuc phien hoc -> ghi session + backend tu tinh streak. */
    @PostMapping
    public SessionDtos.SessionResponse create(@Valid @RequestBody SessionDtos.CreateSessionRequest req,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return sessionService.create(principal.getId(), req);
    }
}
