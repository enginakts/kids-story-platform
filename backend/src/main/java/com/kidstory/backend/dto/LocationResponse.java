package com.kidstory.backend.dto;

import com.kidstory.backend.entity.LocationBible;

public record LocationResponse(Long id, String name, String environmentType, String atmosphereDescription,
                               String lightingStyle) {

    public static LocationResponse from(LocationBible l) {
        return new LocationResponse(l.getId(), l.getName(), l.getEnvironmentType(), l.getAtmosphereDescription(),
                l.getLightingStyle());
    }
}
