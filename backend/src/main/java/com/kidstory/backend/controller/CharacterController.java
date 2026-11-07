package com.kidstory.backend.controller;

import com.kidstory.backend.dto.CharacterOptionsResponse;
import com.kidstory.backend.dto.CharacterRequest;
import com.kidstory.backend.dto.CharacterResponse;
import com.kidstory.backend.dto.LocationResponse;
import com.kidstory.backend.service.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

/** Mobil API: GET listesi önce ebeveynin karakterlerini, sonra sistem şablonlarını döner. */
@ApiController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService service;

    @GetMapping("/characters")
    List<CharacterResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return Stream.concat(service.own(parentId(jwt)).stream(), service.presets().stream()).toList();
    }

    @GetMapping("/characters/options")
    CharacterOptionsResponse options() {
        return CharacterOptionsResponse.create();
    }

    @PostMapping("/characters")
    @ResponseStatus(HttpStatus.CREATED)
    CharacterResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CharacterRequest request) {
        return service.create(parentId(jwt), request);
    }

    @PutMapping("/characters/{id}")
    CharacterResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                             @Valid @RequestBody CharacterRequest request) {
        return service.update(parentId(jwt), id, request);
    }

    @DeleteMapping("/characters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.deactivate(parentId(jwt), id);
    }

    @GetMapping("/locations")
    List<LocationResponse> locations() {
        return service.locations();
    }

    private Long parentId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
