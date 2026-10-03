# Save a plan once, read it without AI

Previously, a generated draft existed only in the HTTP response. Now POST saves
the validated draft before returning 200. GET on the same URL loads the latest
saved snapshot, so refreshing a browser does not run the model.

## Current call flow

POST → ItineraryController → ItineraryService → load trip → Ollama → validate
→ ItineraryStore.save → PostgreSQL → response.

GET → SavedItineraryController → ItineraryStore.get → PostgreSQL → response.

`SavedItineraryController` has no AI profile restriction. That is why saved plans
are readable even if the application starts without `ai` or Ollama is stopped.
Only POST generation needs AI. Neither endpoint makes bookings or verifies prices.

## Why these database choices?

Flyway's `V2__create_itineraries.sql` adds a table without editing the already
applied V1 migration. A foreign key links each snapshot to a real trip. JSONB
stores the nested days, activities and warnings together, fitting this milestone's
whole-plan save/read workflow. We use parameterized SQL through `JdbcTemplate`
in `ItineraryStore`; existing trip storage still uses JPA. Both use the same
configured database and Spring-managed transactions.

Normalized day/activity tables may be useful later for activity-level editing or
reporting. For now JSONB avoids adding several tables just to retrieve one plan.
Future response shape changes must account for already-stored documents.

Every successful generation inserts a new immutable row. GET selects the highest
database-generated ID for the trip. An index supports that lookup. For concurrent
requests, this means insertion order among visible rows, not request start order.
There is no revision-history endpoint or retention cleanup yet.

## Why save after validation?

A model failure or invalid draft never reaches the insert. Previous plans remain
available. The short save transaction commits before POST returns successfully;
a database failure cannot produce a successful save response. Ollama runs outside
that transaction so inference does not hold a database transaction open.

POST is not idempotent: repeating it generates and saves another version. GET is
read-only and returns the same saved data until another successful generation.

## Try it

```bash
# Generate and save (AI enabled)
curl --max-time 210 -X POST http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary

# Retrieve without generating
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary
```

Use an actual trip ID, not the literal placeholder. The second URL also works in
a browser. Restart the app and repeat GET: the database retains the plan.
GET returns 404 for an unknown trip or one without a saved plan.
