package com.masterjp.controller;

import com.masterjp.dto.CommonDtos;
import com.masterjp.dto.SetDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.SetService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sets")
public class SetController {

    private final SetService setService;

    public SetController(SetService setService) {
        this.setService = setService;
    }

    @GetMapping
    public List<SetDtos.SetDto> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        return setService.listMine(principal.getId());
    }

    @PostMapping
    public SetDtos.SetDto create(@Valid @RequestBody SetDtos.CreateSetRequest req,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return setService.create(principal.getId(), req);
    }

    @GetMapping("/public")
    public SetDtos.PublicSetsResponse publicSets(@RequestParam(defaultValue = "") String q,
                                                 @RequestParam(defaultValue = "0") int page) {
        return setService.publicSets(q, page);
    }

    @GetMapping("/{id}")
    public SetDtos.SetDetailDto get(@PathVariable String id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return setService.get(principal == null ? null : principal.getId(), id);
    }

    @PatchMapping("/{id}")
    public SetDtos.SetDto update(@PathVariable String id,
                                 @Valid @RequestBody SetDtos.UpdateSetRequest req,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return setService.update(principal.getId(), id, req);
    }

    @DeleteMapping("/{id}")
    public CommonDtos.MessageResponse delete(@PathVariable String id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = "ADMIN".equals(principal.getRole());
        setService.delete(principal.getId(), id, isAdmin);
        return new CommonDtos.MessageResponse("Đã xóa bộ thẻ.");
    }

    @PostMapping("/{id}/words")
    public SetDtos.AddWordsResponse addWords(@PathVariable String id,
                                             @Valid @RequestBody SetDtos.AddWordsRequest req,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return setService.addWords(principal.getId(), id, req.wordIds());
    }

    @DeleteMapping("/{id}/words/{wordId}")
    public CommonDtos.MessageResponse removeWord(@PathVariable String id,
                                                 @PathVariable String wordId,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        setService.removeWord(principal.getId(), id, wordId);
        return new CommonDtos.MessageResponse("Đã xóa từ khỏi bộ thẻ.");
    }

    @PatchMapping("/{id}/share")
    public SetDtos.SetDto share(@PathVariable String id,
                                @Valid @RequestBody SetDtos.ShareSetRequest req,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return setService.share(principal.getId(), id, req.isPublic());
    }

    @PostMapping("/{id}/fork")
    public SetDtos.ForkResponse fork(@PathVariable String id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return setService.fork(principal.getId(), id);
    }
}
