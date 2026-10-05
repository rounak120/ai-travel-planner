package com.aibot.travelplanner.destination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SelectDestinationRequest(@NotBlank @Size(min = 2, max = 100) String searchName,
                                       @NotNull @Positive Long placeId) {}
