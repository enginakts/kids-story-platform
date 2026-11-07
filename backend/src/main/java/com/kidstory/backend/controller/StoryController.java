package com.kidstory.backend.controller;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.dto.StoryOptionsResponse;
import com.kidstory.backend.dto.StoryRequest;
import com.kidstory.backend.dto.StorySummaryResponse;
import com.kidstory.backend.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mobil API. POST 202 döner: masal arka planda hazırlanır, durum GET ile izlenir. */
@ApiController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    StorySummaryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StoryRequest request) {
        return service.request(parentId(jwt), request);
    }

    @GetMapping
    List<StorySummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(parentId(jwt));
    }

    @GetMapping("/options")
    StoryOptionsResponse options() {
        return StoryOptionsResponse.create();
    }

    @GetMapping("/{id}")
    StoryDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(parentId(jwt), id);
    }

    private Long parentId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
