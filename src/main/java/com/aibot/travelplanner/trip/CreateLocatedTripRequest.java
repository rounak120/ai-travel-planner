package com.aibot.travelplanner.trip;

import com.aibot.travelplanner.destination.SelectDestinationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateLocatedTripRequest(@NotNull @Valid CreateTripRequest trip,
                                      @NotNull @Valid SelectDestinationRequest location) {}
