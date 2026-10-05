# Verification

## Read-only MCP server milestone (2026-10-05)

- Maven suite: **150 tests, zero failures/errors** (`-Ddebug=false test`).
- Added real MCP SDK client/HTTP server tests with deterministic mocked services:
  initialize, discover exactly two tools, required schemas/read-only hints, valid
  destination/weather results, invalid input without service calls, safe tool
  errors, unsupported trip weather, unknown write tool rejection and foreign Origin.
- Local filter unit cases cover rebound Host names, malformed/foreign Origins,
  wrong ports/userinfo/path and allowed same-origin requests. Existing app test
  confirms `/mcp` is absent when the profile is off. Tests do not call public APIs.
- Live app restarted with `ai,mcp`. Manual JSON-RPC initialized a session using
  protocol 2025-06-18, listed `search_destinations` and `get_trip_weather`, and
  called both successfully (`isError: false`). Search returned actual Jaipur
  candidates; weather returned the October 6–7 forecast for the existing demo
  trip. The successful session was closed using DELETE `/mcp`.
- No application data writes, AI calls or new database migration in this check.
  Starter dependency added under the existing Spring AI 2.0.1 BOM. Existing
  itinerary generation still calls services directly, not via MCP.
- Endpoint remains loopback-only and unauthenticated; Host/Origin protection is
  not a replacement for authentication/ownership/rate limits before deployment.

## Weather-aware itinerary milestone (2026-10-05)

- Final Maven suite: **136 tests, zero failures/errors** (`-Ddebug=false test`).
- Generation tests inspect the real Spring AI request against a fake model;
  weather transport remains covered by the existing fake HTTP provider suite.
- Available dated forecasts, null readings, and fetch timestamps enter the prompt
  without database trip IDs. A simulated location change during weather lookup
  does not change the location/forecast snapshot used by that generation.
- Provider failure still yields a validated plan with UNAVAILABLE and no forecast;
  missing selection/date-window cases produce NO_LOCATION/UNSUPPORTED without a
  weather call. Invalid AI output after successful weather lookup saves nothing.
- Saved GET and old-version retrieval retain the original weather data. Old JSON
  lacking weatherContext remains readable, including location-aware snapshots.
- Live POST for Jaipur demo `e99200d3-6d19-4578-ac4f-20cef94fe41c` returned HTTP 200
  in 11.89 seconds with AVAILABLE and a two-day October 6–7 forecast. Saved GET
  matched the complete response, including provider attribution and fetchedAt.
  One new itinerary version was saved; existing versions/locations were unchanged.
- No schema/dependency changes. App restarted with the AI profile on localhost:8080.

## Location-aware itinerary milestone (2026-10-05)

- Maven suite: **134 tests, zero failures/errors** (`-Ddebug=false test`).
- Real Spring AI HTTP payload tested against a fake model: selected place name,
  country and geographic context replace the ambiguous original destination;
  trip budget/currency remain unchanged and database trip IDs are not sent.
- A test changes the current selection while the model request is executing.
  The generated snapshot retains the original selection; a later generation uses
  the new one, and retrieving the old version still returns its original location.
- Legacy JSON with no `selectedLocation` field remains readable as null, without
  backfilling from current trip state. Unselected trips keep text-based generation
  with an explicit warning. No migration or dependency added.
- Live Ollama generation on the two-day Jaipur demo trip returned HTTP 200 in
  10.07 seconds. GET matched the saved response, including provider location
  1269515, Rajasthan, India. One new itinerary version was saved for this demo;
  existing locations, original trip text and prior itinerary versions were not
  changed. No weather or geocoding request is part of this generation flow.
- App restarted with the AI profile on localhost:8080.

## Trip weather milestone (2026-10-05)

- Final Maven suite: **133 tests, zero failures/errors** (`-Ddebug=false test`).
- Fifteen new weather cases use a fake HTTP provider and fixed clock. They cover
  destination-local date boundaries (including UTC being one day earlier), the
  full 16-day window and its last day, past/future/partial coverage rejection,
  missing trip/location/timezone, invalid UUID, request privacy, exact dates/units,
  array lengths, numeric ranges, null measurements, timezone aliases, sanitized
  provider failures and timeouts. No AI or public provider calls in the suite.
- Created a separate development demo trip for Jaipur, October 6–7, 2026:
  `e99200d3-6d19-4578-ac4f-20cef94fe41c`. Selected real provider location 1269515.
  Live GET `/weather` returned HTTP 200 with two dated forecast entries.
- Existing January 2027 sample returned a clear HTTP 422 date-window response;
  no forecast request was needed for that trip. Its pre-existing selected location
  currently uses Asia/Karachi; it was not changed by this milestone.
- No migrations or new dependencies. Existing trips/itineraries remain unchanged
  except for the explicitly added demo trip and its selected location. Forecasts
  are not stored and do not alter itineraries. App restarted with the AI profile.

## Selected trip location milestone (2026-10-05)

- Maven suite: **118 tests, zero failures/errors** (`-Ddebug=false test`).
- New cases verify provider-backed save/read, atomic replacement with one row per
  trip, rollback, failed/missing-choice preservation, invalid requests without
  network calls, trip isolation and V3's foreign key. Tests run without AI and
  use a fake provider; only test-owned trips and cascading selections are cleaned.
- V3 applied to test and development databases. Earlier migrations unchanged.
- Live connectivity now succeeded: Jaipur search returned HTTP 200 and included
  Jaipur, Rajasthan, India (provider ID 1269515), alongside other matches.
- PUT saved that verified search result for the existing Jaipur sample trip:
  HTTP 200. After restarting the app, GET returned HTTP 200 with an identical
  saved location, including attribution and timestamp. Existing trip text and
  itinerary snapshots were not changed. No AI calls were made.
- The old provider-timeout observation below is historical, not the current live
  test result. External availability is still not guaranteed.
- App left running on localhost:8080 with the AI profile.

## Destination lookup milestone (2026-10-05)

- Full Maven suite: **107 tests, zero failures/errors** (`-Ddebug=false test`).
- Twenty-five new fake-provider cases cover response mapping, attribution, no
  credentials, query encoding, absent optional fields, empty results, invalid
  input without network calls, provider failures, invalid coordinates/JSON and
  timeout behavior. The tests run without AI and do not use the public API.
- Live local route returned a sanitized 502 after 5.11 seconds. Direct curl to
  the geocoding provider and its main website also timed out after 15 seconds;
  DNS lookup returned an address. Successful real-provider results are **not
  verified in this environment**. Do not interpret the fake-provider tests as
  evidence that external connectivity works. No network settings were changed.
- App restarted on localhost:8080 with the new route. Existing saved trips and
  itineraries were not changed; no database migration or dependency was added.

## Itinerary history milestone (2026-10-05)

- Maven regression suite: **82 tests, zero failures/errors** (`-Ddebug=false test`).
- Fourteen new cases cover default/max paging bounds, empty history, unknown
  trips and versions, invalid cursors and IDs, exact last pages, ordered metadata,
  an exclusive cursor while a new version is inserted, and trip-scoped lookup.
- History and selected-version tests run without the AI profile. Selecting an
  older version leaves the latest plan and stored row count unchanged.
- Live history GET for the existing Jaipur trip returned HTTP 200 with saved
  version 1. Specific-version and latest GET both returned HTTP 200; the selected
  snapshot's itinerary matched the existing latest plan. No real AI generation
  was needed and no development data was inserted or deleted.
- No schema migration or changes to existing POST/latest GET response shapes.
- Application restarted with the AI profile on localhost:8080.

## Saved itinerary milestone (2026-10-03)

- Final Maven suite: **68 tests, zero failures/errors** (`-Ddebug=false test`).
- V2 migration applied to test and development databases; V1 left unchanged.
- Tests cover POST/GET equality, repeated generations retaining two versions,
  invalid generation preserving the last saved version, GET without model calls,
  committed HTTP reads without the AI profile, transaction rollback, and the
  database foreign key rejecting snapshots for nonexistent trips.
- Test cleanup removes only each test's own trip rows and cascading snapshots.
- Live Jaipur generation: HTTP 200 in 18.64 seconds. Restarted the app and read
  the saved itinerary: HTTP 200 in 0.16 seconds, JSON equal to the POST response.
- App left running with the `ai` profile on localhost:8080. No live places or
  pricing verification added; plans remain unverified AI drafts, not bookings.

The draft-only behavior described in older entries below is historical.

## Lesson 4, step 2 — structured itinerary drafts (2026-10-03)

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B -ntp -Ddebug=false test`
  passed: **65 tests, zero failures/errors**.
- Fake-provider integration checks cover native output schema and token options,
  single-day and seven-day plans, authoritative dates and decimal totals, invalid
  cost precision/sign/budget, missing/null/malformed output, wrong day counts and
  numbering, truncation, missing trips, unsupported lengths and sanitized provider
  errors without retries. Tests do not call the real model.
- AI routes remain absent without the `ai` profile.
- Real local Ollama `qwen3:4b` generated a five-day Jaipur draft for the existing
  saved trip: HTTP 200 in 16.20 seconds, dates January 10–14, 2027, estimated
  listed-activity total INR 6,250.50. No itinerary was saved and no schema migration
  was required. Suggestions are not fact-checked; repetitions and inaccurate
  place details remain a known limitation.
- Initial live generation with thinking enabled returned a safe 502 after
  115 seconds. Disabling thinking specifically for native-schema drafts resolved
  the tested failure; the original connection test keeps its original settings.
- The application was restarted with the `ai` profile on localhost:8080.

Older milestone results below are historical snapshots.

## Lesson 4 — switched to local Ollama on 2026-10-03

Installed Ollama 0.35.1 with Homebrew and downloaded `qwen3:4b`
(ID `359d7dd4bcda`, about 2.5 GB). The Mac has an M1 Pro and 32 GB RAM.
Ollama was started on `127.0.0.1:11434` with `OLLAMA_NO_CLOUD=1`; its log
confirmed cloud features are disabled. It is not configured to start at login.

Replaced the OpenAI starter with the Ollama starter. Maven's dependency tree
confirmed the Ollama starter is present and no OpenAI SDK/starter remains.
The existing ignored `.env` was not modified; its old OpenAI key is unused.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B -ntp -Ddebug=false test
```

Result: **BUILD SUCCESS — 50 tests, 0 failures, 0 errors, 0 skipped.**

- Seven AI cases use a local fake Ollama endpoint, not a loaded model. They
  verify the model/settings, no Authorization header, success/empty output,
  sanitized 404/429/500 failures with no retries, separate thinking excluded
  from the response, and no model calls for GET/health.
- Corrected the retry setting after a failing test exposed that Spring AI
  2.0.1 maps `max-attempts` to retries after the initial call. Zero means one call.
- Live testing found the current `qwen3:4b` tag is the thinking variant.
  `think: true` separates reasoning from final text. Increased the local token
  cap from 128 to 512 because reasoning sometimes exhausted 128 before an answer.
- Restarted the app on port 8080 with the `ai` profile (final PID 51119).
- Real POST `/api/v1/ai/test` returned HTTP 200 with exactly:
  `{"message":"Travel planner AI connection is working."}`.
- `ollama ps` showed 100% GPU usage, approximately 3.2 GB model memory, and a
  4096-token context. Health and retrieval of the existing sample trip passed.
- No OpenAI requests or API charges were involved in this migration.

Netty emits a native macOS DNS-resolver fallback warning. Loopback requests,
the real model call, and all tests succeeded; the optional native resolver
dependency was not added. Itinerary generation and packaging are not yet tested.

Both the app and local Ollama server were left running, alongside PostgreSQL.
Restart commands are in `docs/LESSON_4_AI_CONNECTION.md`.

## Lesson 4, step 1 — original OpenAI setup, verified on 2026-10-03

Added Spring AI 2.0.1 and the opt-in `ai` profile for `gpt-5.4-mini`.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B -ntp -Ddebug=false test
```

Result: **BUILD SUCCESS — 49 tests, 0 failures, 0 errors, 0 skipped.**

- All 42 previous persistence/API tests still pass.
- One additional test checks the AI endpoint is absent without the profile.
- Six AI cases exercise the real Spring AI client against a local fake HTTP
  provider: successful response/options, sanitized 401/429/500 errors without
  retries, empty reply, and no provider calls for GET/health.
- Test configuration overrides both the API key and provider URL. No real
  OpenAI requests were sent, so key validity, billing, and live model access
  are not yet verified.
- Confirmed `.env` is Git-ignored and mode 600; no secret value was printed.
- Restarted the local app with `ai` active (PID 47644, port 8080). Health returned
  UP, GET `/api/v1/ai/test` returned 405 (POST-only route exists), and the
  previous saved trip still returned 200. The billable POST was not invoked.

The app and PostgreSQL were left running. Itinerary generation, live AI
verification, and executable JAR packaging are not included in this step.

## Lesson 3, steps 4–5 — verified on 2026-10-03

`TripService` now uses a JPA repository. Hibernate validated the entity and
collection mappings against the unchanged V1 PostgreSQL schema.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B -ntp -Ddebug=false test
```

Result: **BUILD SUCCESS — 42 tests, 0 failures, 0 errors, 0 skipped.**

- Existing create/read/validation/404/health contracts passed with PostgreSQL.
- The HTTP create test also queried the stored trip and ordered interest rows.
- A same-day trip with an empty interest list was created and retrieved.
- Persistence tests flushed writes, cleared the JPA context, and read matching
  values from the database, preserving interest order and duplicate interests.
- A simulated failure after flushing inserts rolled back both tables.
- API test cleanup deletes only IDs created by each test; transaction tests
  roll back their own data. No broad database cleanup or truncation is used.

### Actual application restart

1. Backed up the known old in-memory demo to `.local/legacy-demo-trip.json`,
   then stopped its process (PID 37275). That old ID was not migrated into PostgreSQL.
2. Started the database-backed app on port 8080 (PID 43400).
3. POSTed a Jaipur trip and received HTTP 201 with ID
   `0b13d217-9b4f-49b7-8f2f-26176657c8a6`.
4. Stopped PID 43400 gracefully. Verified port 8080 no longer accepted HTTP,
   while SQL still returned the trip and all three ordered interests.
5. Started a new Java process (PID 43552) on port 8080.
6. GET of the same ID succeeded; an automated comparison confirmed every JSON
   field matched the original POST response, including `50000.25` and the
   creation time `2026-10-03T06:07:43.654770Z`.

The updated app and PostgreSQL were left running. The sample trip remains in
the development database for inspection:

```text
http://localhost:8080/api/v1/trips/0b13d217-9b4f-49b7-8f2f-26176657c8a6
```

This verifies an application restart, not a machine reboot or disaster recovery.
Executable JAR packaging was not part of this verification.

## Lesson 3, steps 1–3 — verified on 2026-10-02

Installed PostgreSQL 17.11 with Homebrew and initialized a project-local cluster
at `.local/postgres`, listening on `127.0.0.1:5432`. Verified a password-authenticated
connection as the non-superuser `travel_planner`. Created separate development
and test databases, with credentials in an ignored, owner-only `.env` file.

Ran:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B -ntp -Ddebug=false test
```

Result: **BUILD SUCCESS — 40 tests, 0 failures, 0 errors, 0 skipped.**

- All 35 existing API tests passed against the unchanged in-memory service.
- Five PostgreSQL migration tests checked applied version 1, exact budget
  storage and interest ordering, rejected reversed dates/nonpositive budgets,
  and foreign-key enforcement. Inserted test rows were rolled back.
- Flyway applied V1 on the first test context; the next context confirmed the
  schema was up to date without reapplying it.
- A temporary app on port 8081 applied V1 to `ai_travel_planner` and its health
  endpoint returned `status: UP`.
- SQL inspection confirmed `trips`, `trip_interests`, and `flyway_schema_history`
  in the development database, with V1 marked successful. Both databases had
  zero trip rows after verification.

The temporary app was stopped. PostgreSQL remains running; the pre-existing
demo on 8080 was not restarted, so it still runs the earlier configuration.
Restart that app when ready to load the database settings. That restart will
discard its in-memory sample trips. Entity/repository integration and trip
persistence across application restarts have **not** been implemented or tested.

## Lesson 2 — verified on 2026-10-01

Ran on macOS ARM64 with Homebrew OpenJDK 21.0.12.1, Maven 3.9.16, and
the project's pinned Spring Boot 4.1.1:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B -Ddebug=false test
```

Result: **BUILD SUCCESS — 35 tests, 0 failures, 0 errors, 0 skipped.**

- Compiled all 10 application source files and both test classes.
- Original status and health tests passed; status now reports `trip-api`.
- 33 new cases cover trip creation/readback and Location headers, unique IDs,
  money values, date ordering, same-day trips, missing/invalid fields, malformed
  bodies and IDs, missing trips, and immutable interests.
- Integration tests used a real embedded HTTP server on an ephemeral port.
- Maven Central downloads succeeded, including validation and Surefire dependencies.

No long-running server was started as part of this verification. Packaging an
executable JAR was not checked in this lesson; tests do not imply PostgreSQL,
AI, MCP, or UI functionality. Trips still exist only in memory.

## Historical lesson 1 checks

Prepared on 2026-09-28.

### Completed

- Generated the baseline with the official Spring Initializr: Java 21,
  Spring Boot 4.1.1, Maven, Web MVC, and Actuator.
- Parsed `pom.xml` as XML and `application.yml` as YAML.
- Checked application name, health exposure, and pinned Spring Boot version.
- Checked shell syntax for `mvnw` and retained its executable permission.
- Checked project-relative Markdown links and packaged the source with the
  Maven wrapper configuration. The ZIP excludes build output and temporary tools.

### Original build limitation (resolved for compilation/tests above)

`./mvnw clean verify` was attempted with a temporary JDK 21. Maven could not
retrieve the Spring Boot parent POM because repository connectivity from this
workspace was unavailable. This happened before compilation.

At that initial delivery, compilation and integration tests had not been
verified. The successful lesson 2 run above supersedes that limitation.

## Verify locally

With JDK 21, the configured PostgreSQL development/test databases running,
local credentials in `.env`, and access to Maven Central for missing dependencies:

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

Open `/api/v1/status` and `/actuator/health` on http://localhost:8080 as described
in the README. `clean verify` should report 42 tests with no failures and
produce `target/ai-travel-planner-0.0.1-SNAPSHOT.jar`.

Windows users can substitute `./mvnw.cmd` for `./mvnw`.
