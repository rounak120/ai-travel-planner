# Lesson 4, step 2: a draft from your saved trip

The connection test only proved that Ollama could reply. This step gives it a
real task: propose activities for the trip already stored in PostgreSQL.

## How the request works in this project

`POST /api/v1/trips/{id}/itinerary` needs no JSON body. `ItineraryController`
passes the ID to `ItineraryService`, which loads the trip through `TripService`.
The database transaction ends before the slow model call, so inference does not
hold a database transaction open.

`ItineraryGenerator` sends the destination, dates, travelers, budget, currency
and interests to Ollama. The system prompt explains the task; the user's trip
is encoded separately as JSON. This helps distinguish data from instructions,
but is not a guarantee against prompt injection. There are no tools or external
actions available to this model call.

We ask for structured output because Java needs predictable fields, not prose
that happens to describe a plan. `BeanOutputConverter` derives a JSON schema from
`GeneratedItinerary`, and Ollama receives it through its native `format` option.
The response is parsed into that record. A schema helps shape the answer; it does
not establish that the suggestions, prices, or business rules are correct.

## Why Java still validates the answer

The model chooses activity titles, descriptions and rough costs. Java checks
required values, size limits, nonnegative two-decimal costs, the exact number of
days, and consecutive day numbers. Java assigns actual dates from the saved trip
and adds costs with `BigDecimal`, avoiding floating-point money calculations.
Drafts exceeding the trip budget are rejected, not silently altered.

The resulting `ItineraryDraft` carries the authoritative trip ID, currency and
budget. Its warnings make clear that listed activities are only part of the
trip's expenses. No live travel service has verified the suggestions.

## Intentional limits

- One model call per request; no automatic repair or retries.
- Trips of 1–7 days inclusive, to keep local generation bounded.
- Up to 4,096 generated tokens and 8,192 context tokens for this request only.
- Thinking disabled for schema-constrained drafts, to leave the output budget for JSON.
- Up to 180 seconds for the provider read; no guarantee every draft succeeds.
- No itinerary database writes. Repeating POST may produce a different draft.
- No booking, weather lookup, authentication, or public deployment yet.

The existing connection test retains its smaller token limits. Both routes exist
only with the `ai` profile. Provider errors and invalid output return a safe 502,
missing trips return 404, and unsupported trip lengths return 422.

## Try it

```bash
curl --max-time 210 -X POST http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary
```

Replace the ID with one returned by your trip API. Inspect `days`,
`estimatedActivityCostTotal`, and `warnings`. The next milestone will save a
validated draft so it can be retrieved without running the model again.

References: [Spring AI structured output](https://docs.spring.io/spring-ai/reference/api/structured-output.html),
[Ollama structured output](https://docs.ollama.com/capabilities/structured-outputs).
