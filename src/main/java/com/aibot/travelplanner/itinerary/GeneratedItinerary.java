package com.aibot.travelplanner.itinerary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Untrusted model output. Validate it before building the public response. */
public record GeneratedItinerary(
        @NotNull @Size(min = 1, max = 7) List<@NotNull @Valid Day> days
) {
    public record Day(
            @Min(1) int dayNumber,
            @NotNull @Size(min = 1, max = 3) List<@NotNull @Valid Activity> activities
    ) {
    }

    public record Activity(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal estimatedCost
    ) {
    }
}
