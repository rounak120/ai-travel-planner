package com.aibot.travelplanner.trip;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The JSON a caller supplies. The server, not the caller, assigns ID and creation time. */
public record CreateTripRequest(
        @NotBlank @Size(max = 200) String destination,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) @Max(100) Integer travelers,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 2) BigDecimal budget,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter uppercase currency code")
        String currency,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> interests
) {
}
