package com.kidstory.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildProfileResponse {
    private Long id;
    private Long parentId;
    private String name;
    private Integer age;
    private String gender;
    private String favoriteAnimals;
    private String favoriteColors;
    private String specialInterests;
    private String bedtimeHour;
    private String avatarUrl;
    private Boolean isActive;
}
