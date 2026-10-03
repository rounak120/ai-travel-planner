# Lesson 3 — database setup and persistence, steps 1–5

**The Trip API now stores trips in PostgreSQL.** `TripService` uses
`TripRepository` and `TripEntity` instead of an in-memory map. POST creates
database rows, and GET reads them even after the application restarts.

## 1. Local PostgreSQL

On this Mac, Homebrew provides PostgreSQL 17.11 at
`/opt/homebrew/opt/postgresql@17/bin`. The running project-specific cluster uses:

| Setting | Value |
| --- | --- |
| TCP address | `127.0.0.1:5432` |
| Data directory | `.local/postgres/` in this project |
| Server log | `.local/postgres.log` |
| Development database | `ai_travel_planner` |
| Test database | `ai_travel_planner_test` |
| Application login | `travel_planner` |

The application login owns these two databases but is not a cluster superuser
and cannot create databases or roles. TCP connections require its generated
password from `.env`. Local administration uses a trusted Unix socket inside
the owner-only `.local` directory. The server is bound only to loopback.

Homebrew also initialized its default cluster at `/opt/homebrew/var/postgresql@17`.
That cluster is not used here. Use these project-specific commands, not
`brew services start`, to manage this database.

Run commands from the project root. Check whether PostgreSQL is running:

```bash
/opt/homebrew/opt/postgresql@17/bin/pg_ctl -D "$PWD/.local/postgres" status
```

Start it after a reboot (it is not registered to start automatically at login):

```bash
/opt/homebrew/opt/postgresql@17/bin/pg_ctl \
  -D "$PWD/.local/postgres" -l "$PWD/.local/postgres.log" start
```

Stop the application first, then stop PostgreSQL when finished:

```bash
/opt/homebrew/opt/postgresql@17/bin/pg_ctl -D "$PWD/.local/postgres" stop -m fast
```

Stopping PostgreSQL preserves its files. Do not delete `.local/postgres` to
stop or reset the server: that directory holds the actual database. The socket
path in its `postgresql.conf` is specific to this project location.

## 2. Spring connection and dependencies

`application.yml` reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. These can
come from environment variables or the project-root `.env` file, which is
explicitly imported as a properties file. OS environment variables take
precedence. The generated `.env` is ignored by Git and readable/writable only
by its owner. `.env.example` documents the keys without exposing credentials.

The `.env` import is relative to the working directory: run Maven/the JAR from
the project root, or supply the variables when running elsewhere. If setting
this up on another machine, install/start PostgreSQL, create the two databases
and their login, and fill in a new `.env` using `.env.example`.

Added dependencies have distinct jobs:

- `spring-boot-starter-data-jpa`: prepares Spring Data repositories and Hibernate.
- `postgresql`: the JDBC driver that communicates with the database.
- `spring-boot-starter-flyway`: runs versioned migrations during Boot startup.
- `flyway-database-postgresql`: Flyway's database-specific PostgreSQL support.

Spring Boot 4.1.1 manages their versions. Hibernate's `ddl-auto: validate`
prevents it from creating or modifying tables; Flyway owns that job. Hibernate
now validates `TripEntity` against the existing V1 tables on startup.
`open-in-view: false` keeps database access in the service transaction
rather than allowing it to happen while serializing the HTTP response.

Once the server is running, normal commands remain:

```bash
./mvnw spring-boot:run
./mvnw test
```

Tests explicitly activate the `test` profile, whose connection uses
`TEST_DB_URL` (defaulting to `ai_travel_planner_test`). Keep this pointed at a
test database. API tests now exercise the database-backed service and delete
only the rows created by each test. HTTP requests commit on another thread,
so a transaction on the test method would not roll those writes back.
Separate schema and persistence tests use transactions that roll back their data.

## 3. The first migration

`src/main/resources/db/migration/V1__create_trips.sql` creates:

| Table | Why it exists |
| --- | --- |
| `trips` | One row per trip: ID, destination, dates, travelers, budget, currency, and creation timestamp. |
| `trip_interests` | One row per interest, linked to a trip, with its position in the submitted list. |

The database uses `UUID` for IDs, `DATE` for travel dates, `NUMERIC(14,2)` for
budgets, and `TIMESTAMP WITH TIME ZONE` for creation timestamps. The budget
allows 12 integer digits and 2 fractional digits, matching the API's limit.
PostgreSQL can round extra fractional digits on direct numeric inserts; the
API's `@Digits` validation is what rejects such inputs before storage.

Keys and checks prevent duplicate IDs, orphan interests, blank values,
nonpositive budgets, out-of-range traveler counts, and reversed dates. The
interest position is 0–19, with a unique position per trip, so a trip can have
at most 20 interests. Currency format is checked in SQL; recognized codes are
still checked by the service. We do not enforce "today or later" as a database
constraint: old trips must remain valid after their departure dates pass.

Flyway creates a third table, `flyway_schema_history`, to track applied versions
and checksums. `V1__create_trips.sql` means version 1; the separator is **two
underscores**. Once a migration has run, leave it unchanged and add a new `V2`
migration for future changes. Hibernate is not an alternative schema writer.

To inspect the tables using the local application credentials (do not print
the password):

```bash
set -a
source .env
set +a
PGPASSWORD="$DB_PASSWORD" /opt/homebrew/opt/postgresql@17/bin/psql \
  -h 127.0.0.1 -U "$DB_USERNAME" -d ai_travel_planner
```

Inside `psql`:

```sql
\dt
SELECT version, description, success FROM flyway_schema_history;
SELECT count(*) FROM trips;
\q
```

The count increases when you POST a trip. Old in-memory sample trips are not
automatically migrated; the known earlier demo was backed up in the ignored
`.local/legacy-demo-trip.json` before its process was stopped.

## 4. Entity, repository, and service

`TripEntity` maps Java fields to V1's columns. Its ID starts out null, so Spring
Data recognizes it as new; Hibernate generates a UUID when it is persisted.
The protected no-argument constructor lets Hibernate reconstruct a stored row.
The interests list uses `@ElementCollection`, `@CollectionTable`, and
`@OrderColumn` to store each interest with its trip ID and list position.
The existing migration was not modified and no new migration was needed.

`TripRepository extends JpaRepository<TripEntity, UUID>` gives the service
`save` and `findById`. Spring Data supplies the implementation at startup;
there is no hand-written repository implementation or SQL in `TripService`.

`TripService.create` still checks the travel rules and normalizes whitespace,
then saves the entity. `@Transactional` makes the trip row and its interest
rows one unit: both commit, or both roll back. A successful response is not
sent before that transaction commits. Creation timestamps use microsecond
precision and budgets use two decimal places to match PostgreSQL on readback.

`TripService.get` uses a read-only transaction. It maps the loaded entity into
the immutable `Trip` response while the transaction can still load interests.
This avoids exposing a JPA entity or a lazy collection to JSON serialization.
Missing IDs still produce 404; validation failures still produce 400.

```text
POST → TripController → TripService → TripRepository → PostgreSQL
                            ↓
                    immutable Trip response
```

## 5. Verify across an application restart

1. Start PostgreSQL and the application using the commands above.
2. POST the sample in `requests.http` and copy the returned ID.
3. GET that ID and compare the response.
4. Stop **the application** with Ctrl+C, then run `./mvnw spring-boot:run` again.
5. GET the same ID. Its dates, money, ordered interests, and creation time remain.

This was performed with two distinct Java processes; see `VERIFICATION.md`
for the saved trip ID and results. The API paths and JSON field names have
not changed. PostgreSQL still needs to be running; stopping it does not erase
data, but the app cannot serve database-backed trip operations until it returns.

## Official references

- [Spring Boot database initialization and Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [PostgreSQL cluster initialization](https://www.postgresql.org/docs/17/app-initdb.html)
- [Homebrew PostgreSQL 17](https://formulae.brew.sh/formula/postgresql@17)
- [Spring Data JPA saving entities](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)
- [Spring Data JPA transaction boundaries](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
