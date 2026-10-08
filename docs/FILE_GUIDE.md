# Lessons 1–4: what each file does

Paths below are relative to the `ai-travel-planner` project folder.

`src/main/resources/static/` contains the browser chat page (`index.html`), styles
(`chat.css`), and API/rendering code (`chat.js`). `src/test/ui/chat-ui.test.js`
exercises the real page with controlled HTTP responses, launched through installed
Chrome by `scripts/test_chat_ui.py`. See [lesson 15](LESSON_15_CHAT_UI.md).

`ai/TravelAssistantController.java` exposes stateless chat;
`ai/TravelAssistantService.java` owns the bounded model/tool loop and request-scoped
trip binding. `TravelAssistantTests` covers orchestration safety, while
`TravelAssistantHttpTests` checks the REST-to-MCP path with a fake model and provider.
See [lesson 14](LESSON_14_MODEL_TOOLS.md).

MCP client additions: `mcp/TravelMcpClient.java` manages lazy SDK connections and
approved calls; `mcp/McpClientController.java` is the opt-in REST bridge.
`TravelMcpTests` covers real bridge/MCP calls and `McpClientUnavailableTests`
checks client-only startup and safe connection failure. See
[lesson 13](LESSON_13_MCP_CLIENT.md).

MCP adds `mcp/TravelTools.java` (read-only service adapters), `LocalMcpFilter.java`
(local Host/Origin checks), and `application-mcp.yml` (opt-in protocol configuration).
The server starter is managed by the existing Spring AI BOM. `TravelMcpTests`
uses a real SDK client/HTTP server with mocked services; `LocalMcpFilterTests`
checks the guard. `requests-mcp.http` demonstrates protocol calls. See
[the lesson](LESSON_12_MCP_SERVER.md).

Weather-aware planning adds `weather/PlanningWeather.java` for forecast status
and snapshot. `WeatherService.forPlanning` reuses captured trip/location data;
`ItineraryService` calls it before AI; `ItineraryGenerator` supplies the resulting
context; `ItineraryDraft.weatherContext` retains it in each saved version. See
[the lesson](LESSON_11_WEATHER_AWARE_ITINERARIES.md).

Location-aware generation updates `ItineraryService` to read a selection once,
`ItineraryGenerator` to include geographic context, and `ItineraryDraft` to keep
the selection with each version. Legacy JSON without the new field is supported.
No new tables. See [the lesson](LESSON_10_LOCATION_AWARE_ITINERARIES.md).

Package `weather` adds `WeatherController` (GET route), `WeatherService` (trip and
forecast-window checks), `WeatherClient` (provider HTTP/validation), `TripWeather`
(response), `WeatherUnavailableException` (safe errors), and `WeatherConfiguration`
(injectable clock). `WeatherApiTests` uses fixed time and a fake provider. No schema
changes. See [the weather lesson](LESSON_9_TRIP_WEATHER.md).

Selected locations add `TripLocationController`, `TripLocationService` and
`TripLocationStore` under `destination`, plus request/response records
`SelectDestinationRequest` and `SelectedDestination`. Migration
`V3__create_trip_locations.sql` adds one current snapshot per trip. Tests extend
the fake-provider destination suite to cover persistence and failure preservation.
See [the selection lesson](LESSON_8_SELECTED_LOCATION.md).

Destination lookup adds package `destination`: `DestinationController` validates
search requests, `DestinationService` calls Open-Meteo, `DestinationSearch` defines
our public result shape, and `DestinationUnavailableException` provides safe
provider errors. `DestinationApiTests` uses a local fake provider. No database
migration or AI dependency is added. See [the lesson](LESSON_7_DESTINATION_SEARCH.md).

History adds `itinerary/ItineraryHistory.java` for paginated metadata and
`itinerary/ItineraryVersion.java` for a selected saved snapshot. Existing
`ItineraryStore` and `SavedItineraryController` handle the new read-only routes.
`ItineraryHistoryTests` covers paging, trip-scoped lookup and input validation
without AI enabled. See [the history lesson](LESSON_6_ITINERARY_HISTORY.md).

## Itinerary draft files (Lesson 4, step 2)

| File/package | Role in this milestone |
| --- | --- |
| `ai/ItineraryGenerator.java` | Builds the prompt and native JSON schema, calls Ollama, parses the untrusted answer. |
| `itinerary/GeneratedItinerary.java` | Model-output records with validation constraints. |
| `itinerary/ItineraryService.java` | Loads the trip, checks output, assigns dates and sums costs. |
| `itinerary/ItineraryDraft.java` | Public response with authoritative trip fields and explicit estimate warnings. |
| `itinerary/ItineraryController.java` | POST route and safe 404/422/502 errors. |
| `itinerary/InvalidItineraryException.java` | Safe failure message without model or provider internals. |

Java paths in this table start at `src/main/java/com/aibot/travelplanner/`.
See [the current lesson](LESSON_4_ITINERARY.md) for the request flow and rationale.

## Foundation files

Persistence adds `itinerary/ItineraryStore.java` for parameterized snapshot writes
and latest reads, `SavedItineraryController.java` for AI-independent GET requests,
and `ItineraryNotFoundException.java` for missing-plan responses. Flyway migration
`src/main/resources/db/migration/V2__create_itineraries.sql` creates the snapshot
table, foreign key and latest-version index. Existing V1 is unchanged.

| File | Why it exists |
| --- | --- |
| `pom.xml` | Maven's build recipe: project coordinates, Java version, Spring Boot parent, dependencies, test setup, and executable JAR plugin. |
| `mvnw` | Official Maven Wrapper script for macOS/Linux. Downloads the configured Maven version so a separate Maven installation is unnecessary. |
| `mvnw.cmd` | Equivalent wrapper for Windows. |
| `.mvn/wrapper/maven-wrapper.properties` | Pins Maven 3.9.16 and its download URL. Keep this file with both wrapper scripts when sharing or committing the project. |
| `.gitignore` | Excludes generated output, editor files, logs, and local secrets from Git. |
| `.gitattributes` | Keeps wrapper script line endings appropriate across operating systems. |
| `README.md` | The entry point: prerequisites, run commands, expected results, troubleshooting, and lesson order. |
| `requests.http` | Ready-to-run status, health, create-trip, read-trip, and invalid-trip requests for an IDE HTTP client. GET URLs also work in a browser. |
| `docs/ARCHITECTURE.md` | Describes the target design and clearly distinguishes future modules from today's implementation. |
| `docs/FILE_GUIDE.md` | This learning guide, which we will update as we add files. |
| `docs/VERIFICATION.md` | Records validation performed for this delivered milestone. |
| `src/main/java/com/aibot/travelplanner/AiTravelPlannerApplication.java` | The Java entry point. Starts Spring and the embedded HTTP server. |
| `src/main/java/com/aibot/travelplanner/system/StatusController.java` | Receives `GET /api/v1/status`, reads the application name from configuration, and returns a response object. |
| `src/main/java/com/aibot/travelplanner/system/StatusResponse.java` | A Java record describing the three response fields. Keeps the API data shape explicit. |
| `src/main/resources/application.yml` | Configures the application name, local bind address, port, and health exposure. Environment variables can override address and port. |
| `src/test/java/com/aibot/travelplanner/AiTravelPlannerApplicationTests.java` | Starts a real server on a random port and checks the status API and health endpoint over HTTP. |

`target/` appears after a build. Maven puts compiled classes, test reports, and
the JAR there. It is generated output and is not included in the starter archive.
The Initializr-generated `HELP.md` and empty static/template folders were removed
because the project README provides our guide and this lesson has no web UI.

## Understand the first request

1. `main()` calls `SpringApplication.run(...)`.
2. `@SpringBootApplication` enables configuration and scans its package and
   subpackages for Spring-managed classes.
3. Spring finds `@RestController` on `StatusController` and constructs it.
4. The constructor's `@Value` reads `spring.application.name` from YAML.
5. `@RequestMapping("/api/v1")` and `@GetMapping("/status")` combine to form
   `GET /api/v1/status`.
6. `status()` returns a `StatusResponse` record.
7. Spring MVC's JSON converter serializes that record and sends HTTP 200.

There is no hand-written health controller: the Actuator dependency provides
`/actuator/health`. Our status response describes the application milestone;
Actuator reports operational health. Neither means an itinerary was generated.

## Understand the build

The Spring Boot parent in `pom.xml` manages compatible library and plugin
versions. The web starter supplies Spring MVC, JSON handling, and embedded
Tomcat. Actuator supplies operational endpoints. The two test starters supply
test support for the web and Actuator modules. Their `test` scope keeps test
libraries out of the production runtime.

The Spring Boot Maven plugin makes `spring-boot:run` available and repackages
the application into a runnable JAR. `0.0.1-SNAPSHOT` is our development version.

## Files added in lesson 2

Java paths below start at `src/main/java/com/aibot/travelplanner/trip/`.

| File | Why it exists |
| --- | --- |
| `CreateTripRequest.java` | Describes incoming JSON and constraints such as nonblank destination, future/today start date, and positive budget. |
| `Trip.java` | Immutable API response, including generated ID and creation time. Copies interests to prevent later mutation. |
| `TripController.java` | Handles POST and GET, invokes validation, delegates to the service, and returns 201 with a Location header on creation. |
| `TripService.java` | Checks date ordering and currencies; now uses a repository and transaction boundaries to create/read trips. |
| `InvalidTripException.java` | Carries field errors from business rules without coupling the service to HTTP. |
| `TripNotFoundException.java` | Signals a missing trip; the error handler translates it into HTTP 404. |
| `TripExceptionHandler.java` | Converts field/rule failures and malformed requests into readable Problem Detail JSON responses. |
| `src/test/java/com/aibot/travelplanner/trip/TripApiTests.java` (project-relative) | Exercises create/read over real HTTP, invalid inputs, missing trips, and immutable interests. |
| `docs/LESSON_2.md` (project-relative) | Walks through a request and explains validation and the temporary storage tradeoff. |

`pom.xml` now includes `spring-boot-starter-validation`, which supplies the
validation annotations and runtime validator. Status and its test now report
the `trip-api` milestone. No database, AI client, or MCP configuration is needed
for this lesson.

## Lesson 3, steps 1–3: database infrastructure

| File | Why it exists or changed |
| --- | --- |
| `pom.xml` | Adds Spring Data JPA, the PostgreSQL JDBC driver, Spring Boot's Flyway starter, and Flyway's PostgreSQL support module. Versions come from the Boot parent. |
| `src/main/resources/application.yml` | Loads local credentials, configures the database connection, lets Flyway manage the schema, and sets Hibernate to validate rather than create tables. |
| `.env.example` | A shareable template containing connection settings but no real password. |
| `.env` (ignored) | This computer's generated password and local connection details; never commit it. |
| `.local/` (ignored) | This project's PostgreSQL data directory, private local socket, and server log. Database files are durable local data, not build output. |
| `src/main/resources/db/migration/V1__create_trips.sql` | Creates `trips` and `trip_interests` with types, keys, and constraints. |
| `src/test/resources/application-test.yml` | Points the test profile at a separate PostgreSQL database. Existing test classes now activate that profile. |
| `src/test/java/com/aibot/travelplanner/DatabaseMigrationTests.java` | Verifies Flyway and PostgreSQL storage constraints using JDBC; each test transaction rolls back its data. |
| `docs/LESSON_3_DATABASE_SETUP.md` | Explains the database setup, schema decisions, and local start/stop/inspection commands. |

## Lesson 3, steps 4–5: database-backed trips

| File | Why it exists or changed |
| --- | --- |
| `src/main/java/com/aibot/travelplanner/trip/TripEntity.java` | Maps a trip and ordered interests to the existing tables. Converts loaded data to the immutable API response inside the service transaction. |
| `src/main/java/com/aibot/travelplanner/trip/TripRepository.java` | Declares the Spring Data JPA repository, which supplies save/findById. |
| `src/main/java/com/aibot/travelplanner/trip/TripService.java` | Replaces the map with repository calls. Transactions keep trip/interest writes atomic and allow reads to load interests before returning. |
| `src/test/java/com/aibot/travelplanner/trip/TripApiTests.java` | Now asserts database rows after HTTP creation and removes only its own created trips after each test. |
| `src/test/java/com/aibot/travelplanner/trip/TripPersistenceTests.java` | Checks reads after clearing the JPA cache and rollback of both trip and interest rows. |

The migration and the controller's API contract are unchanged. Restart
verification is recorded in `docs/VERIFICATION.md`.

## Lesson 4, step 1: AI connection

- `pom.xml`: adds the Spring AI 2.0.1 BOM and Ollama starter.
- `src/main/resources/application-ai.yml`: opt-in local chat model and URL,
  small output limit, timeout, and no retries. Default configuration disables models.
- `src/main/java/com/aibot/travelplanner/ai/AiConnectionController.java`:
  exposes POST `/api/v1/ai/test` only under the `ai` profile and sanitizes errors.
- `src/main/java/com/aibot/travelplanner/ai/AiConnectionService.java`:
  sends a fixed prompt through Spring AI's ChatClient and checks the reply.
- `src/main/java/com/aibot/travelplanner/ai/AiUnavailableException.java`:
  carries a safe error message without raw provider details.
- `src/test/java/com/aibot/travelplanner/ai/AiConnectionTests.java`:
  tests the full HTTP path against a local fake provider, with no real API calls.
- `docs/LESSON_4_AI_CONNECTION.md`: explains this request flow and the manual test.
