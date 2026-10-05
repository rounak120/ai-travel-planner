# Select and save a trip's location

Update: [location-aware generation](LESSON_10_LOCATION_AWARE_ITINERARIES.md) now
uses this selection in new AI drafts. The original milestone description below
describes selection/persistence before that integration.

Destination search returns candidates. This milestone lets the traveler select
one for a trip and read it later without an external lookup. Coordinates will
be available for weather integration; this step does not fetch weather or change
the AI prompt.

## Request flow in this project

`PUT /api/v1/trips/{tripId}/location` → `TripLocationController` →
`TripLocationService` → load trip → repeat destination search → match place ID →
`TripLocationStore` → PostgreSQL.

The request has only `searchName` and `placeId`. Use the same search text and an
ID from its returned results. The server re-runs the bounded search and copies
the chosen provider record, so caller-supplied coordinates cannot override it.
If results have changed and the ID is absent, the API returns 422: search again.
Only the search text goes to Open-Meteo; the trip ID and trip details stay local.

`GET /api/v1/trips/{tripId}/location` reads only PostgreSQL. It works without the
AI profile and when the external provider is unavailable. The returned snapshot
includes trip ID, place, attribution, source URL and `selectedAt`.

## Why a separate table and store?

Flyway V3 creates `trip_locations` without modifying V1/V2 or existing trips.
The trip ID is both a primary key (one current selection per trip) and a foreign
key (the trip must exist). JSONB stores the selected provider result and its
attribution together, following our existing snapshot-storage approach.

`INSERT ... ON CONFLICT ... DO UPDATE` atomically creates or replaces the row.
There is no delete-then-insert gap. The external search occurs before the short
save transaction. Provider failure or an absent ID never reaches the write, so
an existing selection survives. Concurrent writes are last database write wins;
there is no location history or optimistic-lock conflict handling yet.

We use PUT because callers set the single current location resource, rather
than create an accumulating history. Repeating it does not add rows; it refreshes
the provider snapshot and selection timestamp.

## Try it in order

```bash
# 1. Search and inspect the results; choose the correct country/region.
curl --get http://localhost:8080/api/v1/destinations --data-urlencode 'name=Jaipur'

# 2. Replace both placeholders using a real trip UUID and returned place ID.
curl -X PUT http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/location \
  -H 'Content-Type: application/json' \
  -d '{"searchName":"Jaipur","placeId":REPLACE_WITH_PLACE_ID}'

# 3. Read the saved selection (also works after restarting the app).
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/location
```

PUT/GET success returns 200. Invalid input returns 400, unknown trip or missing
saved location returns 404, absent selected ID returns 422, and provider failure
returns 502. Do not substitute invented coordinates or fake IDs if the public
lookup is unavailable. The previous milestone had provider connection timeouts;
the live search and selection succeeded during this milestone. Automated tests
still use a local fake provider rather than depending on public API availability.

## Intentional limits

The original trip destination text and saved itineraries are unchanged. Users
must select a place appropriate for their trip; we do not infer geographic
equivalence from free text. Until a later integration, AI still uses the trip's
original text rather than this location. No weather, bookings, verified attraction
data, deletion endpoint or authentication is introduced here. Provider attribution
must remain visible in any future UI. Existing Open-Meteo non-commercial usage
terms and request limits still apply.

Provider reference: [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api).
