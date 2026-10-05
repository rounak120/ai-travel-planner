# Give the planner weather context, without inventing forecasts

We already have a weather client and location-aware generation. This step joins
them: Java fetches weather for the captured trip/location before calling Ollama.
The existing POST route stays the same; there is no new endpoint or migration.

## Flow in this project

POST itinerary → load trip and selected location once → validate generation length
→ attempt eligible forecast → assemble AI prompt → validate AI output → save plan
with the selected location AND planning-time weather context.

`WeatherService.forPlanning` uses the captured trip and location, not another
database lookup. If selection changes while weather or AI is running, both calls
and the saved plan still refer to the same original place. External calls are
outside database transactions; only the final valid plan is saved transactionally.

## Why weather can fail without failing the plan

A traveler can plan months ahead, beyond the forecast window. There may also be
a provider outage. Those cases should not make the itinerary feature unusable,
but must not masquerade as successful weather checks. `PlanningWeather` records:

| Status | Meaning |
| --- | --- |
| AVAILABLE | Valid forecast supplied; individual readings may still be null |
| NO_LOCATION | No selected place; no provider call |
| UNSUPPORTED | Trip dates or location timezone unsuitable; no provider call |
| UNAVAILABLE | Provider request failed, timed out, or returned invalid data |

Only known weather validation/provider failures become fallback statuses.
Unexpected programming/database errors are not silently converted into weather
failures. AI failures and validation errors still return 502 and save nothing.

The standalone GET `/weather` keeps its existing error behavior. It cannot return
a forecast when none exists. In contrast, generation can return a plan without
one, with an explicit warning. The five-second weather timeout and no-retry policy
bound additional waiting; existing AI timeout/token limits are unchanged.

## What the model sees

The AI receives weather status/message and, if available, timezone, fetch time
and daily min/max Celsius temperatures plus precipitation in millimetres. It does
not receive database trip IDs in that payload. Instructions suggest sheltered
alternatives for wet/hot days and prohibit treating null readings as zero or
inferring exact hourly conditions from daily totals. The AI still returns only
activities and estimated costs; Java assigns dates and validates totals as before.

This does not guarantee that every suggestion is appropriate. Weather predictions
can change and the model may make mistakes. Attraction existence, opening hours,
prices and bookings remain unverified. No weather alerts or safety guarantees.

## Why save the forecast snapshot?

`weatherContext.forecast` retains the exact provider result supplied to the model,
including attribution and `fetchedAt`. Tomorrow's forecast may differ: reading an
old plan should show its original context, not imply it used tomorrow's data.
GET itinerary/history makes no weather calls. Request GET `/weather` separately
for a fresh forecast, or POST itinerary to generate another version.

Legacy snapshots without `weatherContext` deserialize as null and are not
backfilled. Their original warnings stay intact. Selected-location snapshots
continue working even if they predate this new field.

## Try it

```bash
curl --max-time 210 -X POST http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/itinerary
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/itinerary
```

Inspect `weatherContext.status`, `weatherContext.forecast` and `warnings`. A
selected near-term trip may return AVAILABLE; a distant trip returns UNSUPPORTED
and still generates a plan. No location produces NO_LOCATION. Provider failure
produces UNAVAILABLE rather than a fabricated forecast. No MCP/tool-calling loop
yet: Java explicitly controls the optional lookup followed by one model call.
