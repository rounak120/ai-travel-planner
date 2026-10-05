# Read earlier versions of a trip's plan

Generating twice already saved two snapshots, but previously GET exposed only
the latest. This step makes those older plans accessible without generating them
again. It adds no database migration and does not change existing POST/latest GET
response shapes.

## How this project handles the request

`GET /api/v1/trips/{tripId}/itinerary/versions` reaches `SavedItineraryController`.
The controller checks the optional `limit` and `beforeId` inputs and asks
`ItineraryStore.history` for metadata, not every complete itinerary. The query
uses the existing `(trip_id, id DESC)` index and returns newest snapshots first.
`ItineraryHistory` is the response record. Each entry gives an ID, save time,
currency and estimated activity total so a future UI can show a compact list.

`GET /api/v1/trips/{tripId}/itinerary/versions/{versionId}` returns
`ItineraryVersion`: the selected snapshot's ID, save time and full `itinerary`.
The store filters on BOTH trip ID and snapshot ID. Supplying a valid version ID
under another trip's URL returns 404, not that other trip's data. This check is
not user authorization: authentication and trip ownership still need to be added
before any public deployment.

Both routes read only PostgreSQL. They work without Ollama or the `ai` profile.
Reading an older plan does not select it as the latest or create another row.

## Why pagination and a cursor?

History can grow with each successful POST. We default to 20 results and cap a
request at 50 so one request cannot load all versions. The SQL fetches one extra
row to check whether more results exist. If so, `nextBeforeId` holds the last
returned ID. Send that as `beforeId` on the next request; the query selects
strictly smaller IDs. Otherwise `nextBeforeId` is null and paging is complete.

This avoids shifting pages when newer versions are added during browsing. The
cursor is not a frozen database snapshot: each request sees its own committed
database state. IDs can have gaps and are global across trips; they should not
be displayed as “version 1, version 2” without a separate per-trip numbering design.

## Example

```bash
curl 'http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary/versions?limit=5'
```

Use an actual ID from the returned `versions` list for a specific read:

```bash
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary/versions/REPLACE_WITH_VERSION_ID
```

A known trip with no versions returns an empty list and null cursor. Missing
trips or versions return 404; invalid numeric inputs return 400. History remains
unverified AI-generated suggestions, not confirmed prices or bookings. Editing,
restoring a version, comparison UI and deleting versions are outside this step.
