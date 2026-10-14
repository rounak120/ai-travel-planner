# AI Travel Planner

A Spring Boot application we will grow into a conversational travel planner.
The eventual user supplies a destination, dates, budget, and interests; the
assistant produces an itinerary and revises it with help from travel tools.

The project supports trips, saved itinerary versions, local AI generation,
destination selection, live weather, and opt-in read-only MCP server/client tools.
Model-directed read-only tool calls are available through a stateless chat API;
a local browser chat page is available, while conversation memory remains future work.
**Trips are now stored in PostgreSQL and survive application restarts.**
Spring Data JPA handles storage, and Flyway manages the database schema.
Both the trip API and local AI endpoint need no paid API service or AI key.
Ollama runs the downloaded model on your Mac, using its memory and compute.

## Stack

- Java **21**
- Spring Boot **4.1.1**
- Maven **3.9.16**, downloaded by the included Maven Wrapper
- Spring MVC for HTTP/JSON; Actuator for health; JUnit and AssertJ for tests
- PostgreSQL **17**; Spring Data JPA infrastructure; Flyway schema migrations
- Spring AI **2.0.1** with Ollama **qwen3:4b**, enabled only with the `ai` profile

The scaffold was generated with Spring Initializr and then customized.
Spring AI 2.0.x supports Spring Boot 4.0.x/4.1.x.

## Start on macOS or Linux

1. Install a **JDK 21** and check `java -version` and `javac -version`.
2. Extract the archive and open the `ai-travel-planner` folder in your IDE.
3. Start PostgreSQL and supply database credentials as described in
   [Lesson 3 database setup](docs/LESSON_3_DATABASE_SETUP.md). The development
   database is `ai_travel_planner`; tests use `ai_travel_planner_test`.
   On this Mac both already exist, and `.env` contains the local credentials.
4. In a terminal inside that folder:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

The first run needs internet access to download Maven and dependencies. Maven
does not need to be installed separately. `JAVA_HOME`, if set, must point to the
JDK 21 directory rather than its `bin` subdirectory.

On Windows PowerShell, run `./mvnw.cmd spring-boot:run` instead.
In IntelliJ IDEA, import `pom.xml`, select JDK 21 as the project SDK and Maven
runner JDK, then run `AiTravelPlannerApplication.main()`.

## Check the application

Open http://localhost:8080/api/v1/status in your browser, or run:

```bash
curl http://localhost:8080/api/v1/status
```

Expected JSON:

```json
{"application":"ai-travel-planner","status":"UP","milestone":"trip-api"}
```

Open http://localhost:8080/actuator/health for operational health:

```json
{"status":"UP"}
```

`requests.http` contains status, health, and trip examples for IDE HTTP clients. `/` has no page
yet, so a 404 at http://localhost:8080/ is expected. Stop the server with Ctrl+C.

## Test and package

```bash
./mvnw test
./mvnw clean package
java -jar target/ai-travel-planner-0.0.1-SNAPSHOT.jar
```

PostgreSQL must be running for both application startup and tests. Run commands
from the project root so the local `.env` import resolves. The tests use the
`test` profile and a separate database; they do not write to the development database.

The integration tests start the server on a free port and check status, health,
trip creation/retrieval, and invalid requests. `package` also runs those tests. Stop any running
development instance before starting the JAR on the same port.

If port 8080 is occupied:

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

On PowerShell: `$env:SERVER_PORT=8081` then `./mvnw.cmd spring-boot:run`.
Use the new port in your browser and HTTP requests.

## Read in this order

1. [Architecture](docs/ARCHITECTURE.md): modules, responsibilities, and roadmap.
2. [File guide](docs/FILE_GUIDE.md): every file and why it exists.
3. `AiTravelPlannerApplication.java`, then `StatusController.java`, then
   `StatusResponse.java`: follow the first request through the application.
4. [Verification](docs/VERIFICATION.md): what was checked before delivery.

Read [Lesson 2](docs/LESSON_2.md) for the original request/controller/service
flow, then [Lesson 3](docs/LESSON_3_DATABASE_SETUP.md) for database persistence.
Continue with [Lesson 4, step 1](docs/LESSON_4_AI_CONNECTION.md) for the AI connection.

## Create and read a trip

Start the application, then run this in another terminal. Choose dates that
are today or later; these sample dates will eventually need updating.

```bash
curl -i http://localhost:8080/api/v1/trips \
  -H 'Content-Type: application/json' \
  -d '{"destination":"Jaipur","startDate":"2027-01-10","endDate":"2027-01-14","travelers":2,"budget":50000,"currency":"INR","interests":["food","history"]}'
```

Expect HTTP `201 Created`, a `Location: /api/v1/trips/<id>` header, and a JSON
trip containing the supplied fields plus its generated `id` and `createdAt`.
Use that actual ID to read the saved trip:

```bash
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_ID
```

Replace `REPLACE_WITH_ID` before running the command. Invalid fields return `400` with
field errors; an unknown ID returns `404`. Creating a trip does not call AI.

To verify persistence, create a trip, record its ID, stop the application with
Ctrl+C, start it again, and GET the same ID. PostgreSQL must stay available.
Trips from the former in-memory implementation are not automatically migrated.

## Optional AI connection test

Install Ollama once with `brew install ollama`. Start it in a separate terminal
with cloud features disabled and loopback-only access:

```bash
OLLAMA_NO_CLOUD=1 OLLAMA_HOST=127.0.0.1:11434 ollama serve
```

Download the model once (about 2.5 GB), then start the app with AI enabled:

```bash
ollama pull qwen3:4b
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai
```

Stop any old app before reusing port 8080. No API key is needed. Starting the
app and checking health do not generate text. This POST runs the local model:

```bash
curl -i -X POST http://localhost:8080/api/v1/ai/test
```

Expected: HTTP 200 with a `message` containing the model's short reply.
Provider failures or empty replies return a sanitized HTTP 502. Without the
`ai` profile this endpoint does not exist (404). Automated tests use a fake
local provider, so the test suite does not require Ollama or load a real model.

## Generate an itinerary draft

With the `ai` profile and Ollama running, use an existing trip ID:

```bash
curl --max-time 210 -X POST http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary
```

No request body is needed: preferences come from the saved trip. HTTP 200 returns
dated activities, estimated activity costs, and warnings. Valid drafts are saved
in PostgreSQL before a successful response is returned. Each successful POST adds
a version; failures leave previous versions untouched.
This version supports 1–7 days inclusive (longer trips return 422). Unknown trips
return 404; invalid, truncated or unavailable model output returns a sanitized 502.
Generation can take a minute or more; the provider read timeout is 180 seconds.
Activity estimates are for the whole party and exclude accommodation, intercity
travel and unlisted meals. They are not verified prices or a complete trip quote.

Generation lesson: [Structured itinerary drafts](docs/LESSON_4_ITINERARY.md).
Read the latest saved version without calling Ollama (also works without `ai`):

```bash
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary
```

GET returns 404 if the trip is missing or has no saved itinerary. You can open
the GET URL in a browser; replace the placeholder with an actual trip UUID.
For concurrent generation, latest means highest database-assigned snapshot ID,
not the request that started last. Older snapshots remain accessible through
the version-history API.

See [saving and reading itineraries](docs/LESSON_5_SAVED_ITINERARIES.md).

## Read previous itinerary versions

```bash
# Newest first; default 20 results, maximum 50
curl 'http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary/versions?limit=5'

# Use an actual id from the versions array
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_ID/itinerary/versions/REPLACE_WITH_VERSION_ID
```

History lists each snapshot's `id`, `createdAt`, currency and estimated activity
cost. If `nextBeforeId` is non-null, pass it as `beforeId` with the next list
request. An empty history returns 200 with an empty array; an unknown trip or
version returns 404. Invalid limits, cursors and ID formats return 400.
Version IDs are global database IDs, not consecutive numbers within one trip.
Reading an older version does not change the latest itinerary or call AI.

History lesson: [Itinerary version history](docs/LESSON_6_ITINERARY_HISTORY.md).

## Search destinations

```bash
curl --get 'http://localhost:8080/api/v1/destinations' --data-urlencode 'name=Jaipur'
```

Returns up to five location matches with country, region, coordinates, timezone
and attribution. Search names must be 2–100 characters after trimming. No matches
returns an empty `results` array (200); invalid input returns 400; provider failure
or timeout returns a safe 502. No AI profile or API key is required.

This route sends the search text over the internet to Open-Meteo. It does not
send saved trip details, select a match, save coordinates or change itineraries.
Only an explicit destination search calls the provider; app startup and health
checks do not. Each request has a five-second timeout and no application retries.

Open-Meteo's free endpoint is for non-commercial use and is rate-limited. We have
not added caching or a rate limiter, so keep this local learning API private and
revisit provider terms before deployment. Location data comes from GeoNames via
Open-Meteo; preserve returned attribution in any future UI. This is city/location
search, not verification of attractions, prices, opening hours or weather.

Search lesson: [Destination search](docs/LESSON_7_DESTINATION_SEARCH.md).

## Select a trip location

After searching, submit the same search text and an actual returned place ID:

```bash
curl -X PUT http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/location \
  -H 'Content-Type: application/json' \
  -d '{"searchName":"Jaipur","placeId":REPLACE_WITH_PLACE_ID}'

curl http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/location
```

Replace the placeholders before running. PUT rechecks the search result with
Open-Meteo and atomically saves one current location per trip. GET reads the
saved location without external calls. Provider failure (502) or an ID no longer
in the results (422) preserves the prior selection; invalid input returns 400.
GET returns 404 for a missing trip or a trip without a selected location.

This does not modify the original trip text or existing itineraries. New itinerary
generations now use the current selected location when one exists and retain it
in the returned/saved `selectedLocation` field. Without a selection, generation
uses the original text with an explicit warning. Changing a selection does not
retroactively change old itinerary versions.
Selection lesson:
[Selected trip location](docs/LESSON_8_SELECTED_LOCATION.md).

## Weather for a trip

```bash
curl http://localhost:8080/api/v1/trips/REPLACE_WITH_TRIP_ID/weather
```

Requires a saved location with a valid timezone. Returns daily min/max temperature
in Celsius and precipitation in millimetres for the exact trip dates, with source,
fetch timestamp and warnings. Null measurements mean unavailable, not zero.

All trip dates must lie from today through today + 15 days in the destination's
timezone. A trip outside or partly outside that window returns 422 with the
supported dates; no substitute dates or fabricated long-range forecast are used.
Unknown trips or missing selections return 404; unavailable/invalid provider
responses return a safe 502. Each successful lookup is fresh: no cache, persistence,
AI call or itinerary update. The provider receives coordinates, dates and timezone,
not the trip ID, budget, travelers or interests. The existing five-second network
timeout pattern and non-commercial provider usage limits apply.

Weather lesson: [Trip weather](docs/LESSON_9_TRIP_WEATHER.md).

Location lesson: [Location-aware itinerary generation](docs/LESSON_10_LOCATION_AWARE_ITINERARIES.md).
The existing POST `/api/v1/trips/{id}/itinerary` now includes selected geographic
context in the AI request. When selected, the response's `destination` is the
provider place name; country/region/coordinates and attribution are preserved in
`selectedLocation`. Older versions without that field read it as null.

## Weather-aware itinerary generation

The existing itinerary POST now attempts one forecast lookup for a selected
location and eligible trip dates before asking Ollama to plan. The model receives
dated daily measurements, including nulls for missing data; it is instructed not
to invent hourly weather or forecasts when none were provided.

The saved `weatherContext` records status, message, and the forecast snapshot
(including source and fetch time) when available:

- `AVAILABLE`: a validated forecast was supplied to the model.
- `NO_LOCATION`: no selection, so no weather request was made.
- `UNSUPPORTED`: dates/timezone unsuitable; no weather request was made.
- `UNAVAILABLE`: the provider failed or returned invalid data; planning continued.

Weather failure does not block planning, but invalid AI output still does. Saved
GET/history reads never refresh weather or regenerate activities. Old versions
without `weatherContext` return null: they do not acquire a forecast retroactively.
GET `/weather` remains available for a fresh lookup and retains its 404/422/502
errors; unlike generation it does not have a fallback plan to return.

Planning lesson: [Weather-aware itinerary generation](docs/LESSON_11_WEATHER_AWARE_ITINERARIES.md).

## Optional read-only MCP server

```bash
# MCP tools only (Ollama not needed)
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp

# Or enable both the existing AI endpoints and MCP
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai,mcp
```

Stop the old app before reusing port 8080. Streamable HTTP endpoint:
`http://127.0.0.1:8080/mcp`. Tools: `search_destinations(name)` and
`get_trip_weather(tripId)`. MCP is disabled without the `mcp` profile.
Initialization/discovery do not call providers. Tool calls reuse existing services
and preserve source attribution, date-window rules and five-second provider timeouts.

This is an MCP JSON-RPC endpoint, not a normal browser page. Clients initialize
a session, list tools and then call them. See [requests-mcp.http](requests-mcp.http)
or the [MCP lesson](docs/LESSON_12_MCP_SERVER.md) for the tested request sequence.
The current itinerary flow still calls Java services directly; Ollama is not yet
connected to these tools through an MCP client.

Keep the existing loopback-only bind. MCP has no authentication or trip ownership
checks; any local client with access can call the tools. Host/Origin checks reduce
DNS-rebinding/cross-origin exposure, but do not replace authentication. No create,
update, delete, booking or itinerary-generation tools are published. Read-only
tools still use provider quota and can return saved location/weather data. Do not
expose `/mcp` through tunnels or public binds without a proper security boundary.

## Explicit MCP client

Enable both sides (add `ai` if you also want Ollama):

```bash
./mvnw -Dspring-boot.run.profiles=mcp,mcp-client spring-boot:run
curl http://localhost:8080/api/v1/mcp/tools
curl 'http://localhost:8080/api/v1/mcp/destinations?name=Jaipur'
curl http://localhost:8080/api/v1/mcp/trips/YOUR_TRIP_ID/weather
```

These REST endpoints make real MCP HTTP calls to this app's loopback `/mcp`.
They are absent without `mcp-client`, do not call Ollama, and save nothing.
HTTP 200 with `isError: true` means the protocol exchange succeeded but the tool
failed; check `structuredContent`. Invalid REST input returns 400; MCP connection
or protocol failures return 502. Keep these unauthenticated endpoints local too.

## Model-directed travel assistant

With `ai,mcp,mcp-client` active, Ollama can request destination searches and trip
weather through MCP. No trip or itinerary data is modified.

```bash
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Search Jaipur and tell me its country and region."}'
```

Optional `tripId` enables weather for that fixed trip. Responses contain `message`
and actual MCP results in `tools`. Limits: 2,000 input characters, two tool calls,
three model rounds. No chat history, automatic retries or write tools.

## Browser chat

The itinerary page now includes **Load / refresh history**, paginated older
versions, and read-only opening of specific saved IDs. It preserves each version's
original context and does not restore or regenerate it. See
[Lesson 20: itinerary history UI](docs/LESSON_20_ITINERARY_HISTORY_UI.md).

View or generate plans at **http://localhost:8080/itinerary.html**. Loading reads
the latest saved version without AI; explicit generation calls Ollama and saves a
new version. Trips of 1–7 days are supported. See
[Lesson 19: itinerary UI](docs/LESSON_19_ITINERARY_UI.md).

Select the exact destination at **http://localhost:8080/location.html**: load a
saved trip, search, review country/region, then explicitly save one result.
Trip creation and chat both link to this page. No automatic location selection
or AI writes. See [Lesson 18: location selection UI](docs/LESSON_18_LOCATION_UI.md).

Create a saved trip at **http://localhost:8080/trips.html**, then open it in chat
with its ID prefilled. This uses the existing trip API and does not invoke AI.
A saved destination name still needs a location selection before weather works.
See [Lesson 17: trip creation form](docs/LESSON_17_TRIP_FORM.md).

Open **http://localhost:8080/**. The page uses the existing chat API and displays
answers separately from expandable MCP evidence. Start the app with
`ai,mcp,mcp-client` to submit questions. Optional Trip ID enables weather.

Each question is independent; the visible cards are not model memory. The page
stores nothing, makes no background model calls, and does not modify trips.
No frontend dependencies or separate build are needed.

Current lesson: [Browser chat UI](docs/LESSON_15_CHAT_UI.md).

Weather validation now preserves distinct `INVALID_TIMEZONE` and
`DATES_OUTSIDE_FORECAST_WINDOW` errors across REST, MCP and chat. For these errors,
chat returns the application's exact explanation without model reinterpretation.
See [Lesson 16: precise weather errors](docs/LESSON_16_WEATHER_ERRORS.md).

## Local development boundary

This starter binds to `127.0.0.1` and has no authentication. Authentication and
authorization must be added before deploying user-specific travel features.
Do not expose the AI endpoint or Ollama publicly. Only Actuator health is exposed.
Keep provider keys in environment
variables; do not commit them. `.env` files are ignored by Git and are now
explicitly imported by `application.yml` as Java properties. Use unquoted values
as shown in `.env.example`; Spring Boot does not implicitly parse shell dotenv syntax.

## Official references

- [Spring Initializr](https://start.spring.io/)
- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html)
