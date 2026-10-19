# Accounts, editing, revisions and memory

## Use the new workflows

1. Open `/account.html`. Create your own username/password and sign in. No account
   or password was created for you by the implementation process.
2. Create a trip (type a destination and select its matching city before saving)
   or load **My Trips** on the dashboard. The six older trips were
   preserved as unassigned, per your choice. They are hidden from every account;
   there is no public claim/import endpoint. A future administrator-assisted
   assignment must be an explicit, separately authorized database operation.
3. Open a trip and select **Edit trip**. Load the form, change fields, and save.
   The start date must be today or later when editing, as with creation. Changing
   destination text clears its selected location; changing dates/budget does not.
   Existing itinerary snapshots remain unchanged and may no longer match the trip.
4. **Delete trip** requires confirmation and permanently deletes the trip,
   interests, location and itinerary history. Removing a browser shortcut still
   removes only the shortcut. No live trip was deleted during verification.
5. On Itineraries, load history and open a specific saved version. Enter a request
   such as “make day 1 cheaper” and confirm **Generate revised version**. The model
   receives the chosen draft plus current trip constraints and fresh weather
   context. Valid output becomes a new snapshot; failures leave the original intact.
6. Chat remembers the last three successful user/assistant turns in your server
   session. User text and retained assistant text are each capped at 2,000 characters
   per turn. Tool result JSON is not retained as message history. Context switches
   when trip scope changes. **Clear conversation** resets memory and visible cards.

Memory is not a saved chat archive. Page reload clears only the visible transcript;
sign-out, session expiry (30 minutes idle), or app restart discards server memory.
Reauthentication clears memory too. Messages remain untrusted context; current tool
evidence must be fetched again. Different browser tabs in one signed-in session
share the same memory and current trip scope. Requests in that session are serialized
around inference to preserve order. Different users never share memory.

## How and why this project does it

- `SecurityConfiguration` uses Spring Security's form-login/session handling,
  including session-ID rotation on login. Passwords use BCrypt (cost 12).
- `AccountController` registers lowercase usernames, returns the CSRF token, and
  reports the signed-in user. No password hashes are returned through an API.
- `CurrentUser` reads the authenticated identity. The client never supplies a trip
  owner. `TripService` filters listing and checks ownership for read/update/delete;
  downstream itinerary, location and weather services reuse those checks.
- `McpIdentityConfiguration` captures the authenticated HTTP principal in MCP
  transport context. `TravelTools` restores it only around that call, including
  cleanup in `finally`. The internal MCP client forwards the session and CSRF token
  to the fixed loopback endpoint, not to external providers.
- CSRF remains enabled for registration, login, logout, REST writes and MCP POSTs.
  `app.js` fetches a current token before writes. Cookies are HttpOnly and
  SameSite=Strict. Passwords and tokens are never stored in localStorage.
- Browser shortcuts are cleared at login/logout to avoid showing another account's
  destination names; other tabs reload on the account-change storage event.
- PUT updates within a transaction. Existing database foreign keys perform deletion
  cascades. Revisions reuse the itinerary validation and persistence path instead
  of overwriting JSON directly. Editing is last-write-wins; multi-tab conflict UI
  and optimistic edit tokens are not implemented yet.

## API additions and authentication

| Method/path | Purpose |
| --- | --- |
| GET `/api/v1/auth/csrf` | Get `{headerName, token}` and an anonymous session if needed |
| POST `/api/v1/auth/register` | JSON `{username, password}`; requires CSRF |
| POST `/api/v1/auth/login` | URL-encoded `username` and `password`; requires CSRF |
| GET `/api/v1/auth/me` | Current account; 401 when not signed in |
| POST `/api/v1/auth/logout` | Invalidate session; requires CSRF |
| PUT `/api/v1/trips/{id}` | Full trip fields, same validation as creation |
| DELETE `/api/v1/trips/{id}` | Permanent deletion; returns 204 |
| POST `/api/v1/trips/{id}/itinerary/revisions` | `{versionId, instruction}`; 1–2000 characters |
| DELETE `/api/v1/ai/chat/memory` | Clear server-session conversation; AI/chat profiles required |

Command-line clients must keep the cookie returned by `/auth/csrf`, submit login
with the CSRF header, keep the rotated cookie, and fetch a new token after login.
Subsequent reads require the cookie; writes also require the returned CSRF header.
Anonymous API reads return 401; missing/invalid CSRF on writes returns 403.
Missing or foreign trip/version IDs return 404 without confirming ownership.

This remains a local learning application, not a publicly deployable account system.
Email verification, password reset, login throttling, HTTPS/Secure cookies, audit
and retention policies, and production backup/restore procedures remain future work.
Do not expose the app or Ollama through public binds/tunnels.

## Verification and references

The tests use the same real security filter chain as the app. Fixture users log in
over HTTP, send session cookies and CSRF tokens, and use only the test database.
Two-user checks cover trip routes and MCP, unassigned data, revisions, memory,
registration/hashing, session rotation, CSRF rejection and logout. Browser tests
use fake APIs and isolated profiles; no development accounts/trips are created.

Implementation follows Spring Security's documented
[session authentication and persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
and [CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
