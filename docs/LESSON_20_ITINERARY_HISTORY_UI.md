# Lesson 20: browse saved itinerary versions

On **http://localhost:8080/itinerary.html**, load your trip, then click **Load /
refresh history**. Choose **View saved ID …** to open a snapshot, or **Load older
versions** to fetch another page. Loading history and opening a version never
generate a new itinerary, change the latest version, or refresh weather.

## Why this matters in our project

Each successful generation already saves an immutable itinerary version. Until
now the browser showed only the latest plan; the history API was available only
through direct requests. This step connects that existing API to the page.

| Action | Request | Result |
| --- | --- | --- |
| Refresh history | GET itinerary/versions?limit=10 | Newest ten metadata entries |
| Load older | GET itinerary/versions?limit=10&beforeId=… | Entries older than the returned cursor |
| View saved ID | GET itinerary/versions/{id} | One trip-scoped snapshot |
| Return to latest | Load trip & latest plan | Read the latest saved plan again |

The entry's ID is a database snapshot ID, not “version 1, 2, 3” for this trip.
Creation timestamps are shown as returned by the server, including their timezone
suffix. The list includes the activity estimate and currency but does not fetch
every full itinerary in advance.

## How pagination works

The backend returns `nextBeforeId`. The browser uses that exact value for the next
request instead of guessing a page number. A null cursor means the listing has
no more entries. Refresh starts from the newest entries and replaces the list.
Loaded rows are deduplicated by ID; this does not promise a live, automatically
updating view if another tab generates a plan. Refresh to see newly saved versions.

## Keeping the displayed plan honest

`showPlan` now labels a result as latest-at-load-time, newly generated, or a specific
saved ID with its creation time. Opening an older plan renders that snapshot's
original location, weather, warnings and estimates. Current trip metadata above
the plan is not used to rewrite the historical snapshot.

History actions use the page's existing busy state: you cannot change the trip or
start generation while a history request is pending. Opening a version also resets
generation consent. Changing the trip, reloading latest, or successfully generating
clears history state. A failed list/version request preserves the previous display
and reports the error; it never silently generates a replacement.

The browser checks the returned version ID and itinerary trip ID before replacing
the display. The backend already scopes version retrieval to the requested trip.
All server text is rendered as text, not HTML. These safeguards are not user
authentication or ownership checks; keep the app local.

## Boundaries of this step

No schema, backend business rule, model prompt or dependency changes. Reading
history works without Ollama or the `ai` profile. There is no restore, deletion,
comparison or editing action in this UI. Opening an older version does not make
it the latest; those would be separate features with explicit write semantics.

Browser regression checks cover pagination, read-only requests, historical context,
empty lists, request failures, reset behavior and the existing generation workflow.
They use fake API responses and do not write to the development database:

```bash
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 1280,1000 /itinerary.html
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 390,844 /itinerary.html
```
