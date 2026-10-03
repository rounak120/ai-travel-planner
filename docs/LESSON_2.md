# Lesson 2: create and retrieve a trip

This page records the original in-memory lesson. The current application now
uses PostgreSQL; see [Lesson 3](LESSON_3_DATABASE_SETUP.md) for the replacement
storage flow. The request validation and HTTP concepts below still apply.

The diagram's **Spring Boot REST API → Trip services** path now works.
We first collect structured travel preferences; AI-generated itineraries will
use these preferences in a later lesson.

## Follow one request

```text
POST /api/v1/trips + JSON
  → CreateTripRequest: decode JSON and validate individual fields
  → TripController: delegate the create operation
  → TripService: check travel rules, create a Trip, store it in memory
  → HTTP 201 + Location header + Trip JSON

GET /api/v1/trips/{id}
  → TripController → TripService → same stored Trip → HTTP 200
```

1. `CreateTripRequest` is a **record**, a concise Java data carrier. Spring
   converts the JSON body into this record. `@RequestBody` selects the body;
   `@Valid` makes Spring check its validation annotations before entering the
   controller method. Callers provide travel preferences, not the ID or timestamp.
2. `TripController` is the HTTP entry point. Its constructor receives a
   `TripService` that Spring creates and supplies (dependency injection).
   The controller selects the success status and the saved resource's URL.
3. `TripService` runs the use case. It checks that the end date is not before
   the start date and that the currency code is recognized. It generates a
   UUID and creation timestamp only after these checks pass.
4. `Trip` represents the saved result. Dates use `LocalDate`, creation time
   uses `Instant`, and money uses `BigDecimal` to avoid binary floating-point
   rounding. The budget is the total for the whole trip, not per traveler/day.
5. The service saves that record in a `ConcurrentHashMap`. Concurrent HTTP
   requests can access the map safely, and returned interests are immutable.
6. `ResponseEntity.created(...)` produces HTTP `201 Created` and a `Location`
   header identifying the GET URL. Spring serializes the Trip into JSON.

We use `Trip` directly as the response for now because every stored field is
public API data. When internal or owner fields arrive, a separate response
object can keep those details out of the API.

## Validation rules

| Field | Rule |
| --- | --- |
| `destination` | Required, nonblank, at most 200 characters; surrounding whitespace removed after validation. |
| `startDate` | Required, today or later according to the server's local date. |
| `endDate` | Required, on or after `startDate`; single-day trips are allowed. |
| `travelers` | Required integer from 1 through 100. |
| `budget` | Required, greater than zero, at most 12 integer digits and 2 decimal places. |
| `currency` | Required, recognized three-letter uppercase currency code, e.g. INR or USD. |
| `interests` | Required list, may be empty, at most 20 entries; each nonblank and at most 80 characters. |

The two-decimal budget limit is this lesson's API rule, not a claim that every
currency has two minor-unit digits. Currency-specific precision comes later.

Individual field constraints live on `CreateTripRequest`. Cross-field date
ordering and recognized currency checks live in `TripService`. For now all
callers enter through the validated REST controller; future MCP tools must also
validate requests before invoking the service.

## Useful errors

`InvalidTripException` and `TripNotFoundException` express failures without
HTTP imports in the service. `TripExceptionHandler` maps them to HTTP responses
and extends Spring's handler for malformed JSON, invalid IDs, and other MVC errors.
Validation failures return `application/problem+json`, for example:

```json
{
  "type": "about:blank",
  "title": "Invalid trip request",
  "status": 400,
  "detail": "Check the highlighted trip fields.",
  "instance": "/api/v1/trips",
  "errors": { "endDate": "must be on or after startDate" }
}
```

An unknown but well-formed UUID returns `404`; a malformed UUID returns `400`.
Field validation runs before service rules, so fix reported fields and retry
to see any remaining cross-field errors.

## Try it and understand the limit

Run `./mvnw test`, then `./mvnw spring-boot:run`. If an older instance is already
running, stop it first. Use the examples in `requests.http` or `README.md`.
POST a trip, copy its ID into the GET request, and try a negative budget.

The map is per application process: restarting loses all trips, and two
instances do not share data. There is no eviction or durable storage yet, so
this is a small local learning app. Lesson 3 replaces the map with PostgreSQL,
JPA, and Flyway migrations. It will not recover trips from previous restarts.

## References

- [Spring MVC validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [Spring MVC Problem Detail responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
