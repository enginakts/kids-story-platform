package com.kidstory.backend.controller;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.dto.StoryOptionsResponse;
import com.kidstory.backend.dto.StoryRequest;
import com.kidstory.backend.dto.StorySummaryResponse;
import com.kidstory.backend.service.StoryAudioService;
import com.kidstory.backend.service.StoryImageService;
import com.kidstory.backend.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/** Mobil API. POST 202 döner: masal arka planda hazırlanır, durum GET ile izlenir. */
@ApiController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService service;
    private final StoryAudioService audio;
    private final StoryImageService images;

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

    /** Sahne sesi (MP3). Range istekleri desteklenir; yalnızca masalın sahibi indirebilir. */
    @GetMapping("/{id}/scenes/{number}/audio")
    ResponseEntity<Resource> sceneAudio(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable int number) {
        return audioResponse(audio.file(parentId(jwt), id, number));
    }

    /** Hazırlanamamış sahne seslerini yeniden kuyruğa koyar. 202 döner; durum GET /{id} ile izlenir. */
    @PostMapping("/{id}/audio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    StoryDetailResponse.Progress requestAudio(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        audio.requestAudio(parentId(jwt), id);
        return service.get(parentId(jwt), id).audio();
    }

    /** Sahne resmi. Yalnızca masalın sahibi indirebilir. Kapak, 1. sahnenin resmidir. */
    @GetMapping("/{id}/scenes/{number}/image")
    ResponseEntity<Resource> sceneImage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable int number) {
        StoryImageService.ImageFile file = images.file(parentId(jwt), id, number);
        return media(file.resource(), file.mimeType());
    }

    /** Hazırlanamamış sahne resimlerini yeniden kuyruğa koyar. */
    @PostMapping("/{id}/images")
    @ResponseStatus(HttpStatus.ACCEPTED)
    StoryDetailResponse.Progress requestImages(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        images.requestImages(parentId(jwt), id);
        return service.get(parentId(jwt), id).images();
    }

    static ResponseEntity<Resource> media(Resource resource, String mimeType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(resource);
    }

    static ResponseEntity<Resource> audioResponse(StoryAudioService.AudioFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(file.resource());
    }

    private Long parentId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
