# The complete travel workspace

Start at http://localhost:8080/dashboard.html. Spring Boot serves the HTML,
CSS and JavaScript from `src/main/resources/static`; the same origin serves JSON
APIs, so there is no separate frontend server or CORS setup.

Sign in first via the **Sign in / account** link. Legacy trips are unassigned and
hidden; new trips belong to your account. See [account guide](ACCOUNTS_AND_REVISIONS.md).

## Screens and their purpose

| Screen | What it does | Existing API used |
| --- | --- | --- |
| `/dashboard.html` | Browse My Trips, open details, save/remove browser shortcuts, download trip JSON | GET `/api/v1/trips?page=0&size=10` and GET `/api/v1/trips/{id}` |
| `/trips.html` | Suggest destinations while typing; select a city and save trip/location together | GET `/api/v1/destinations`, POST `/api/v1/trips/with-location` |
| `/edit.html?tripId=…` | Load and edit a trip | GET then PUT `/api/v1/trips/{id}` |
| `/account.html` | Register, sign in, sign out | `/api/v1/auth/*` |
| `/location.html` | Search destinations, review and explicitly save the selected place | Trip/location GETs, destination search GET, location PUT |
| `/weather.html` | Show daily forecast, source, timezone and exact availability errors | Trip GET then trip/weather GET |
| `/` | Ask the session-aware AI assistant; inspect actual tool results; clear memory | POST `/api/v1/ai/chat`, DELETE `/api/v1/ai/chat/memory` |
| `/itinerary.html` | Read latest/history, generate a new snapshot, download or print the viewed plan | Trip and itinerary GETs; explicit confirmed itinerary POST |

The AI and MCP profiles are needed for AI chat, and the AI profile for itinerary
generation. Viewing existing data does not require a model call. Ollama must be
running for generation/chat. Weather and location search need their external
provider to be reachable.

## How and why this project connects the screens

`app.js` builds shared navigation and provides safe DOM, request, export and
browser-storage helpers. Each page keeps its own workflow logic. This avoids
duplicating navigation across six pages without adding a framework or build step.

A validated `tripId` query parameter follows navigation. It pre-fills a form but
does not fetch, generate, or change anything automatically. Editing a trip ID
clears old context. Explicit buttons distinguish reads from writes; generating
an itinerary still requires confirmation. Busy states prevent duplicate submits.

My Trips explicitly loads database records in ten-trip pages, newest first.
Previous/Next browse pages; refresh returns to the newest page. Failed reads preserve
the previous page and explain that it remains shown. Card buttons open trip details.
The list includes only the signed-in user's trips. The server enforces ownership
for details, edits, deletion, location, weather, itinerary history/revisions and MCP.
The separate optional shortcuts are a maximum of ten `{id, destination}`
entries under `wayfarer.shortcuts.v1` in localStorage. They are saved only after
an explicit click, remain on this browser, and are not an account or synced trip
library. Clearing browser data removes shortcuts, not PostgreSQL records.
Do not save shortcuts on a shared browser if destination names are sensitive.

The weather page calls the deterministic API directly, not Ollama. It retains
the server's exact timezone/date-window explanation rather than guessing why
weather failed. A new request clears the previous forecast. Null readings say
“Unavailable”; a real zero remains zero. Forecasts still require all trip dates
within the server's supported 16-day window in the selected place's timezone.

Itinerary downloads contain the snapshot currently being viewed, including its
stored evidence and warnings. Printing hides navigation and action controls;
use the browser's save-as-PDF option for a printable copy. Neither action
generates a plan, refreshes weather, or changes saved versions.

Provider, user and model content is rendered as text rather than HTML. Shared
styles include keyboard focus, a skip link, current-page navigation, narrow-screen
layouts and print rules. No analytics, remote fonts or image assets are loaded.

## Verification

Java integration tests verify that Spring Boot serves the new assets alongside
the existing backend tests. Browser tests use fake API responses in an isolated
Chrome profile, so they do not write to development trips or call Ollama/providers.

Run a screen's browser suite with installed Chrome and Python 3:

```bash
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 1280,1000 '/dashboard.html'
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 390,844 '/weather.html'
```

Other supported paths are `/trips.html`, `/location.html`, `/itinerary.html`, and
`/?tripId=e99200d3-6d19-4578-ac4f-20cef94fe41c` for chat's contextual tests.
Fixtures do not depend on that trip existing in your database.

This completes the frontend for existing backend features, not production
booking, password recovery, login throttling or production deployment hardening.
Keep the app bound to localhost.
