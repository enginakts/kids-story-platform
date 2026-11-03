package com.kidstory.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record CreateChildRequest(
        @NotBlank(message = "Takma ad zorunludur") @Size(max = 100) String name,
        @NotNull(message = "Yaş zorunludur") @Min(value = 3, message = "Yaş en az 3 olmalıdır")
        @Max(value = 7, message = "Yaş en fazla 7 olabilir") Integer age,
        @Size(max = 20) String gender,
        @Size(max = 500) String favoriteAnimals,
        @Size(max = 500) String favoriteColors,
        @Size(max = 500) String specialInterests,
        LocalTime bedtimeHour) {
}
