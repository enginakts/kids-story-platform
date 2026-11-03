package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.service.ChildProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ApiController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildProfileController {

    private final ChildProfileService service;

    @GetMapping
    List<ChildProfileResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(parentId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ChildProfileResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateChildRequest request) {
        return service.create(parentId(jwt), request);
    }

    @PutMapping("/{id}")
    ChildProfileResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                         @Valid @RequestBody CreateChildRequest request) {
        return service.update(parentId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.deactivate(parentId(jwt), id);
    }

    private Long parentId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
