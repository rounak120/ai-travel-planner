# Lesson 19: view and generate itineraries in the browser

Open **http://localhost:8080/itinerary.html**, or use the itinerary link from chat
or the location-selection page. Enter a saved trip ID and click **Load trip & latest
plan**. Opening the page with `?tripId=...` only prefills that field.

## The important separation

| Action | API | Model/storage behavior |
| --- | --- | --- |
| Load trip and latest plan | GET trip, then GET trip/itinerary | Reads saved data only; no model or weather refresh |
| Generate and save | POST trip/itinerary | Calls the existing generator and saves a new validated version |

This is why our separate saved-itinerary and generation controllers are useful.
The page needs no new backend controller, schema or dependency. Viewing works
without the AI profile; generation needs `ai` and a running local Ollama model.
The `mcp` profiles are not required for this itinerary workflow, which still uses
the existing Java services directly. Chat has its own MCP-based flow.

`itinerary.html` defines the page, `itinerary.js` calls these APIs, and the shared
stylesheet provides the layout. The browser does not calculate costs or rebuild
dates: it displays the totals and dates already validated and assigned by Java.

## Why generation is explicit

Generating consumes local model resources and writes a new version. A checkbox
and a separate button make that action clear. Controls are locked during the
request, and confirmation resets afterward to prevent accidental repeated clicks.
There is no automatic retry, generation on navigation, or automatic overwrite of
old versions. This page shows only the latest result; a history UI is a later step.

If generation fails, an already displayed plan remains visible. The status explains
that it is the previous successful result, not the failed new generation. A lost
network response may hide a successful save: load the latest plan before retrying.
Closing the page does not cancel work already running on the server. This is not
server-side idempotency or cross-tab concurrency control.

## Reading the plan correctly

- Day cards show activities, descriptions, individual estimates and daily totals.
- The top shows activity total, currency and trip budget. No currency conversion
  or additional browser arithmetic is performed.
- Warnings remain visible: these are estimates for the whole party, not verified
  prices or bookings. Accommodation and other unlisted costs are excluded.
- Location and weather are saved snapshots. A displayed forecast fetch time is
  not evidence of a fresh lookup. The full JSON snapshot includes attribution.
- Older plans without those snapshot fields display an explicit missing-context
  message, rather than failing or inventing provenance.
- Model/provider strings are inserted as plain text, never executable HTML.

Generation still supports only 1–7-day trips. Unsupported weather dates do not
necessarily block itinerary generation: the existing service can generate a draft
with an honest unsupported-weather warning. Selecting a location is recommended,
but the existing no-selection fallback remains intact. Generation does not use
earlier chat messages; it uses stored trip preferences and current planning context.

## Verify without calling a model

Browser tests use fake API responses, never real POSTs to the running app:

```bash
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 1280,1000 /itinerary.html
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 390,844 /itinerary.html
```

They check read/write separation, explicit consent, duplicate prevention, legacy
snapshots, safe text, warnings, costs, generation errors, ambiguous network outcomes
and changing the selected trip. Backend generation/storage remains covered by the
existing Java integration tests. Keep this unauthenticated app on localhost.
