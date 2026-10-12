# Lesson 17: create a saved trip from the browser

Open **http://localhost:8080/trips.html**, or choose **Create a new trip** on the
chat homepage. This step replaces the trip-creation curl command with a form.
It does not add AI writes, automatic itinerary generation or location selection.

## How it works in this project

```text
Trip form → POST /api/v1/trips → TripController → TripService → PostgreSQL
         ← HTTP 201 + saved trip UUID
         → Open this trip in chat → /?tripId=UUID (prefill only)
```

`trips.html` defines the fields and explains what will be saved. `trips.js` sends
the existing API's request format; the backend remains unchanged. It trims the
destination, converts currency to uppercase, and splits comma-separated interests
into an array. Budget is sent as a decimal string so Java can parse it directly
into `BigDecimal` without browser floating-point arithmetic. An API regression
test verifies the maximum supported budget is stored and retrieved exactly.

Browser validation catches missing fields, reversed dates, traveler/budget limits
and oversized interests. The API remains authoritative for rules such as a
recognized currency, a start date that is not past, and decimal precision. API
field errors appear below the form as plain text, with entered values preserved.

## What happens after Create trip

The page disables the form during saving. HTTP 201 displays the saved trip ID,
a link to retrieve the saved JSON, and a link to chat with that ID prefilled.
It stays locked after success to prevent accidentally creating the same trip
again. **Create another trip** explicitly resets the form; it does not delete
the previous saved trip.

Opening the chat link does not submit a question or call a provider. `chat.js`
accepts only a full UUID from the query parameter. The ID is in the URL and may
appear in browser history—it is not an authentication credential. Keep this
unauthenticated app on localhost. No additional browser storage is used.

A network failure is an uncertain outcome: the server might have saved the trip
before the response was lost. The page warns about this and does not retry
automatically. This is not server-side idempotency; refreshing and submitting the
same data can still create another trip. Idempotency is a separate future step.

## Important limitation

A destination string such as “Jaipur” is not yet a selected geographic location.
Weather still needs location selection through the existing API, plus all trip
dates within the current forecast window. The form does not auto-select a place
or promise that weather is ready. A location-selection UI is the next small step.

Trip creation needs PostgreSQL but not Ollama or AI profiles. Chat still requires
`ai,mcp,mcp-client`. No new frontend dependencies or database migrations.

## Verification

The Java suite checks static asset delivery and persistence of the browser's exact
decimal budget. Browser tests use fake API responses and create no real trips:

```bash
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 1280,1000 /trips.html
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 390,844 /trips.html
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 1280,1000 '/?tripId=e99200d3-6d19-4578-ac4f-20cef94fe41c'
```
