package com.kidstory.backend.controller;

import com.kidstory.backend.dto.CreateStoryRequest;
import com.kidstory.backend.dto.StoryViews;
import com.kidstory.backend.service.story.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Mobil masal uçları. Üretim asenkrondur: POST 202 döner, istemci GET /stories/{id} ile durumu izler.
 * Görsel ve ses: GET /stories/{id}/scenes/{n}/image ve /audio (yalnızca masalın sahibi).
 */
@ApiController
@RequiredArgsConstructor
public class StoryController {

    private final StoryService stories;

    @GetMapping("/api/v1/children/{childId}/story-options")
    StoryViews.Options options(@AuthenticationPrincipal Jwt jwt, @PathVariable Long childId) {
        return stories.options(parentId(jwt), childId);
    }

    @GetMapping("/api/v1/children/{childId}/stories")
    List<StoryViews.Summary> listForChild(@AuthenticationPrincipal Jwt jwt, @PathVariable Long childId) {
        return stories.listForChild(parentId(jwt), childId);
    }

    @PostMapping("/api/v1/stories")
    @ResponseStatus(HttpStatus.ACCEPTED)
    StoryViews.Detail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStoryRequest request) {
        return stories.create(parentId(jwt), request);
    }

    @GetMapping("/api/v1/stories/{id}")
    StoryViews.Detail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return stories.get(parentId(jwt), id);
    }

    @GetMapping("/api/v1/stories/{id}/scenes/{number}/image")
    ResponseEntity<Resource> image(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable int number) {
        return MediaResponses.of(stories.media(parentId(jwt), id, number, StoryService.Media.IMAGE));
    }

    @GetMapping("/api/v1/stories/{id}/scenes/{number}/audio")
    ResponseEntity<Resource> audio(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable int number) {
        return MediaResponses.of(stories.media(parentId(jwt), id, number, StoryService.Media.AUDIO));
    }

    private static Long parentId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
