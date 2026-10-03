# Verification

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
