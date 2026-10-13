# Lesson 18: select the exact destination in the browser

Open **http://localhost:8080/location.html**. You can also follow the location link
after creating a trip, or from chat. The link carries only the trip ID; navigation
does not automatically search or save anything.

## How this fits our project

The new page uses three existing APIs, without adding backend business logic:

| User action | API | Effect |
| --- | --- | --- |
| Load trip | GET trip, then GET trip/location | Read trip and current selection |
| Search | GET /api/v1/destinations?name=... | Search Open-Meteo; no database write |
| Save selected location | PUT trip/location with searchName and placeId | Revalidate and save the user's choice |

The trip's free-text destination is still the original trip preference. Its saved
selected location supplies the specific coordinates and timezone for weather and
future itinerary generation. Previously saved itinerary snapshots do not change.

`location.html` defines the form and guidance; `location.js` handles loading,
searching, selection and saving. It reuses the existing styles. No AI call, new
dependency, migration or frontend build is involved.

## Why we make the choice explicit

There can be several places called Jaipur. Search results show country, region,
coordinates and timezone. No radio button is selected automatically. The user
must choose one and click **Save selected location**, with a visible warning that
this replaces any current selection.

Java remains authoritative: TripLocationService searches again when saving and
accepts only a place ID in those fresh results. The browser sends the captured
search string and ID—not client-supplied coordinates or timezone. If a match is
no longer present, saving fails and the user must search again.

Changing the trip ID clears the loaded trip and choices. Changing the search text
clears previous results. Controls are disabled during requests; saved/error choices
are cleared afterward. These rules keep stale candidates from being applied to a
different trip or query. They are UI safeguards, not authentication or concurrency
control across multiple browser tabs.

## Error handling and limitations

- Missing trips cannot proceed to search. A missing selection for an existing
  trip is shown as “No selected location yet.”
- Search failures and empty results allow a new search without changing the
  current saved location.
- A failed save keeps the last loaded selection visible. If the network fails,
  it may have saved on the server; load the trip again to check. No automatic retry.
- Provider and user data are rendered as plain text. Source attribution is shown
  with both current selections and search results.
- A missing timezone is clearly labelled. Such a place may be saved by the
  existing API, but cannot be used for weather.
- Selecting a location does not alter dates. Past trips and dates outside the
  forecast window are still ineligible. This is not a promise of forecast availability.

The page needs PostgreSQL; searching/saving also needs the geocoding provider.
Ollama is not needed until you ask a chat question. Keep the unauthenticated app
on localhost. The trip ID is not an authorization token.

## Try it

1. Create a trip with appropriate dates, or use an existing saved trip ID.
2. Open its location link and click **Load trip**.
3. Search, compare the countries and regions, choose the right result, and save.
4. Follow **Open this trip in chat** to ask about weather. The ID is prefilled.

Browser checks use controlled responses and do not modify real trips:

```bash
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 1280,1000 /location.html
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 390,844 /location.html
```
