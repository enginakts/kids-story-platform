package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.service.ChildProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Çocuk Profilleri Controller'ı
 * .NET Core Karşılığı: [Authorize] [Route("api/[controller]")] public class ChildrenController : ControllerBase
 */
@RestController
@RequestMapping("/api/children")
@RequiredArgsConstructor
public class ChildProfileController {

    private final ChildProfileService childProfileService;

    /**
     * Ebeveyne ait çocuk profillerini listeler
     * GET /api/children?parentId=1
     */
    @GetMapping
    public ResponseEntity<List<ChildProfileResponse>> getChildren(@RequestParam(required = false) Long parentId) {
        if (parentId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<ChildProfileResponse> children = childProfileService.getChildrenByParent(parentId);
        return ResponseEntity.ok(children);
    }

    /**
     * Yeni çocuk profili ekler
     * POST /api/children?parentId=1
     */
    @PostMapping
    public ResponseEntity<ChildProfileResponse> createChild(
            @RequestParam Long parentId,
            @Valid @RequestBody CreateChildRequest request) {
        ChildProfileResponse response = childProfileService.createChild(parentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
