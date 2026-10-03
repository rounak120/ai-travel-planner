# AI Travel Planner — lesson 4, step 1: AI connection

A Spring Boot application we will grow into a conversational travel planner.
The eventual user supplies a destination, dates, budget, and interests; the
assistant produces an itinerary and revises it with help from travel tools.

**This milestone implements trip creation/retrieval, validation, status, health,
and an opt-in AI connection test.** Itinerary generation, MCP, live travel data,
and the chat UI are planned features.
**Trips are now stored in PostgreSQL and survive application restarts.**
Spring Data JPA handles storage, and Flyway manages the database schema.
The trip API needs no paid service. The optional AI endpoint uses a paid OpenAI API key.

## Stack

- Java **21**
- Spring Boot **4.1.1**
- Maven **3.9.16**, downloaded by the included Maven Wrapper
- Spring MVC for HTTP/JSON; Actuator for health; JUnit and AssertJ for tests
- PostgreSQL **17**; Spring Data JPA infrastructure; Flyway schema migrations
- Spring AI **2.0.1** with OpenAI **gpt-5.4-mini**, enabled only with the `ai` profile

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
field errors; an unknown ID returns `404`. No itinerary is generated yet.

To verify persistence, create a trip, record its ID, stop the application with
Ctrl+C, start it again, and GET the same ID. PostgreSQL must stay available.
Trips from the former in-memory implementation are not automatically migrated.

## Optional AI connection test

Add `OPENAI_API_KEY` to your ignored `.env` file, then stop the old app and run:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai
```

Starting the app and checking health do not send AI requests. The following
explicit POST makes one potentially billable request (no automatic retries):

```bash
curl -i -X POST http://localhost:8080/api/v1/ai/test
```

Expected: HTTP 200 with a `message` containing the model's short reply.
Provider failures or empty replies return a sanitized HTTP 502. Without the
`ai` profile this endpoint does not exist (404). Automated tests use a fake
local provider and fake key; no API credits are used.

Current lesson: [AI connection](docs/LESSON_4_AI_CONNECTION.md).
Next step: generate a structured itinerary from a saved trip.

## Local development boundary

This starter binds to `127.0.0.1` and has no authentication. Authentication and
authorization must be added before deploying user-specific travel features.
Do not expose the billable AI endpoint publicly. Only Actuator health is exposed.
Keep provider keys in environment
variables; do not commit them. `.env` files are ignored by Git and are now
explicitly imported by `application.yml` as Java properties. Use unquoted values
as shown in `.env.example`; Spring Boot does not implicitly parse shell dotenv syntax.

## Official references

- [Spring Initializr](https://start.spring.io/)
- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html)
