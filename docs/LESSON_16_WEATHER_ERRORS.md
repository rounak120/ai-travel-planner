# Lesson 16: preserve the real reason a weather request failed

Our October 6–7 demo trip has a valid `Asia/Kolkata` timezone. On October 8, its
dates are already past. The weather REST API explained this correctly, but MCP
replaced every 422 with a message mentioning both timezone and dates. Ollama then
guessed that the timezone was missing.

## What changed

`WeatherEligibilityException` now represents two distinct application-owned
validation errors:

| Code | Meaning | Additional context |
| --- | --- | --- |
| `INVALID_TIMEZONE` | Saved location timezone is missing or invalid | None; raw invalid input is not echoed |
| `DATES_OUTSIDE_FORECAST_WINDOW` | At least one trip date is outside today's allowed window | Trip dates, forecast window, validated timezone |

`WeatherService` creates these errors where it performs the checks, before any
weather-provider call. The exception extends `ResponseStatusException`, retaining
HTTP 422 and the existing itinerary-planning fallback behavior.

`WeatherController` exposes `code` and `context` alongside the normal problem
`detail`. `TravelTools` preserves the same safe details in the MCP result:

```json
{
  "isError": true,
  "structuredContent": {
    "code": "DATES_OUTSIDE_FORECAST_WINDOW",
    "message": "Trip dates ... are outside the supported forecast window ...",
    "context": {
      "tripStartDate": "2026-10-06",
      "tripEndDate": "2026-10-07",
      "forecastStartDate": "2026-10-08",
      "forecastEndDate": "2026-10-23",
      "timezone": "Asia/Kolkata"
    }
  }
}
```

The dates above illustrate October 8; the real window is calculated dynamically
in the selected location's timezone. It includes today and the next 15 days.

## Why this is more than a prompt change

For these two specific weather errors, `TravelAssistantService` returns the
application's message directly with the existing tool evidence. It does not make
a second model call to reinterpret the failure. The model still selects the tool,
but it cannot turn this returned date error into a missing-timezone diagnosis.
If a batch includes other tool results, those remain visible in the evidence;
the final message focuses on the weather validation failure.

Normal successful answers and other tool errors retain the existing model flow.
Unknown 422 errors stay generic and sanitized; arbitrary exception messages are
not exposed through MCP. No parsing of exception message strings is used to infer
the error type. No trip dates or saved locations are changed.

## Check it

Restart with `ai,mcp,mcp-client`, refresh the homepage, and submit the original
question with the original trip ID. The new response should report the trip dates
and current forecast window, not a missing timezone. Old cards are just previous
responses; they are not rewritten automatically.

You can also compare:

```bash
curl http://localhost:8080/api/v1/trips/e99200d3-6d19-4578-ac4f-20cef94fe41c/weather
curl http://localhost:8080/api/v1/mcp/trips/e99200d3-6d19-4578-ac4f-20cef94fe41c/weather
```

REST still returns HTTP 422; the MCP bridge still returns an HTTP 200 tool envelope
with `isError: true`. Their code, context and explanation now agree.

Regression tests check missing/invalid timezone, past/future/partially covered
dates, precise MCP errors, and deterministic chat responses without another model
call. A full HTTP test compares REST and chat evidence through real MCP transport
with a fixed clock and a valid Jaipur timezone. It verifies zero provider calls.
