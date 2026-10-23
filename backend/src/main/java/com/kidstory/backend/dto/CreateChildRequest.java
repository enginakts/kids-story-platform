package com.kidstory.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateChildRequest {
    @NotBlank(message = "Çocuk adı zorunludur")
    private String name;

    @NotNull(message = "Yaş zorunludur")
    @Min(value = 2, message = "Yaş en az 2 olmalıdır")
    @Max(value = 8, message = "Yaş en fazla 8 olabilir")
    private Integer age;

    private String gender;
    private String favoriteAnimals;
    private String favoriteColors;
    private String specialInterests;
    private String bedtimeHour;
    private String avatarUrl;
}
