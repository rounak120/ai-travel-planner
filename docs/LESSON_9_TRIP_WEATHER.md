# Weather for the selected location and trip dates

Update: the standalone GET behavior below remains unchanged, while
[weather-aware generation](LESSON_11_WEATHER_AWARE_ITINERARIES.md) now reuses this
service to supply an optional forecast to new itinerary generations.

We now have a saved trip and a selected geographic location. This milestone adds
`GET /api/v1/trips/{tripId}/weather`, using those two inputs to fetch a forecast.
It is separate from the AI itinerary: no generated text, bookings or automatic
activity changes are involved.

## Request flow

WeatherController → WeatherService → load trip and selected location → validate
timezone and date window → WeatherClient → Open-Meteo → validate → TripWeather.

`WeatherService` checks dates before making any external call. `WeatherClient`
handles URL construction, timeouts and provider JSON. `TripWeather` describes the
public response, using field names that explicitly identify Celsius and millimetres.
The original trip and saved location stay unchanged; no new table is needed.

## Why the forecast-window check matters

The provider supports up to 16 forecast days. Our first version accepts trips
entirely within today through today + 15 days, inclusive. A January trip requested
in October gets 422, not October weather relabelled as January weather. Past or
partially covered trips are also rejected; we do not silently trim the dates.

“Today” is calculated in the saved destination's timezone. It may already be
tomorrow there even if the server's UTC clock still says yesterday. An injected
`Clock` makes boundary tests deterministic. Missing/invalid timezone produces 422;
missing trip or selected location produces 404. Actual model coverage may still
have missing values or fail even for dates within the accepted window.

## Provider validation and missing data

The outgoing request specifies exact dates, timezone, daily minimum/maximum
temperature, precipitation sum, Celsius and millimetres. Only coordinates, dates,
timezone and requested variables leave the app, not personal trip preferences.
The client checks units, equivalent timezone rules, exact date sequence, matching
array lengths, numeric values, minimum ≤ maximum and nonnegative precipitation.
Timezone aliases are accepted when their Java timezone rules are equal.

A null measurement stays null and has an explicit warning; it is not converted
to zero. Precipitation is total water equivalent, including rain and snow, not a
probability of rain. `fetchedAt` is the retrieval time, not a model issue time.
Forecasts are predictions, may change, and are not a severe-weather alert service.

Failures and timeouts become a safe 502. Requests use a five-second timeout,
no automatic application retries and no redirects. No database transaction stays
open during inference or the external weather request. Reads do not require AI.

## Try it

1. Create a short trip within the forecast window.
2. Search for the destination and save the correct location using PUT `/location`.
3. GET `/api/v1/trips/{tripId}/weather`.

```bash
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/weather
```

Your existing January 2027 sample is useful for checking the explicit 422 date
guard while those dates are too far away. Use a near-term trip to see forecasts.
Tests freeze time and use a local fake provider, including null readings, malformed
data, timeouts and boundary dates; they never call the public service.

## Scope and provider terms

This endpoint is a direct REST integration, not MCP or AI tool calling yet. It
does not save forecasts, change itineraries or infer weather for unforecastable
dates. There is no cache/rate limiter; keep the unauthenticated app local and
preserve attribution when displaying data. The free API is for non-commercial
use and subject to provider limits. Configuration is under `travel.weather`.

Reference: [Open-Meteo forecast documentation](https://open-meteo.com/en/docs),
[provider terms](https://open-meteo.com/en/terms).
