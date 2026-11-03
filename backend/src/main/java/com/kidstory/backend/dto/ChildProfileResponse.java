package com.kidstory.backend.dto;

import com.kidstory.backend.entity.ChildProfile;

import java.time.LocalTime;

public record ChildProfileResponse(Long id, String name, Integer age, String gender, String favoriteAnimals,
                            String favoriteColors, String specialInterests, LocalTime bedtimeHour, String avatarKey) {

    public static ChildProfileResponse from(ChildProfile c) {
        return new ChildProfileResponse(c.getId(), c.getName(), c.getAge(), c.getGender(), c.getFavoriteAnimals(),
                c.getFavoriteColors(), c.getSpecialInterests(), c.getBedtimeHour(), c.getAvatarKey());
    }
}
