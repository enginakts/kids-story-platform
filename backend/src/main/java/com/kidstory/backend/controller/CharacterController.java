package com.kidstory.backend.controller;

import com.kidstory.backend.dto.CharacterResponse;
import com.kidstory.backend.dto.LocationResponse;
import com.kidstory.backend.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Mobil API: yöneticilerin tanımladığı, yayındaki karakter ve mekânlar. Ebeveynler yalnızca okur;
 * karakter ve mekân ekleme, düzenleme yönetim panelindedir (V10).
 */
@ApiController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService service;

    @GetMapping("/characters")
    List<CharacterResponse> list() {
        return service.presets();
    }

    @GetMapping("/locations")
    List<LocationResponse> locations() {
        return service.locations();
    }
}
