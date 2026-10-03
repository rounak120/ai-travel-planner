# Lesson 4, step 1 — connect the model

This step checks one thing: can our Spring Boot app send a short prompt through
Spring AI and return the model's reply? It does not generate or save an itinerary.
The existing trip API, database tables, and migration are unchanged.

## How the request moves through this project

`POST /api/v1/ai/test` → `AiConnectionController` → `AiConnectionService`
→ Spring AI `ChatClient` → OpenAI → JSON response.

- The controller owns HTTP: route, response shape, and a safe 502 error response.
- The service owns the prompt and model call. It uses a fixed prompt, so this
  test sends no trip records or personal data to OpenAI.
- Spring AI handles the provider HTTP request and response parsing. The
  service uses its `ChatClient` interface rather than assembling HTTP manually.
- PostgreSQL is not involved in this particular request; the rest of the
  application still uses PostgreSQL as before.

## Why we added these settings

`pom.xml` imports the Spring AI 2.0.1 BOM (a list of compatible library versions)
and the OpenAI starter. Spring Boot remains 4.1.1 and Java remains 21.

`application.yml` disables AI models by default. `application-ai.yml` enables
only chat when the `ai` profile is active. The controller and service have
`@Profile("ai")` for the same reason: plain trip development should not require
an AI key. Embeddings, image, audio, and moderation clients remain disabled.

The AI settings select `gpt-5.4-mini`, read `OPENAI_API_KEY` from the existing
local configuration, cap completion tokens at 128, use reasoning effort `none`,
set a 20-second provider timeout, and disable automatic retries. `store: false`
disables stored completions; it is not a promise of zero provider data retention.
These deliberately small limits are for a connection test, not a full itinerary.

The key name `travel-planner` is just a label in your OpenAI dashboard. The
secret key value is the credential the application needs. Keep it only in your
ignored `.env` or an environment variable, never in Java/YAML or Git.
`OPENAI_MODEL` can override the default model, but other models may need different options.

## Start and test manually

Keep your database settings and add the secret locally to `.env`:

```dotenv
OPENAI_API_KEY=replace-with-your-secret-key
```

Stop the old application before reusing port 8080. From the project root:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai
```

Startup and `/actuator/health` never invoke the model. To make a paid API call:

```bash
curl -i -X POST http://localhost:8080/api/v1/ai/test
```

An illustrative successful response is:

```json
{"message":"Travel planner AI connection is working."}
```

The text comes from the model, not a hard-coded success response. A 502 means
the provider call failed or returned no text. Check the key, model access, API
billing/quota, and connectivity in your provider dashboard. Raw provider errors
are deliberately not returned or logged by our service because they can contain
sensitive details. A 404 means the `ai` profile is not active; GET returns 405
when the profile is active. Restart after changing `.env`.

Do not publish this unauthenticated endpoint. Each POST can consume credits;
the application remains bound to loopback. Authentication and rate limits are
required before shared deployment.

## What our automated tests prove

`AiConnectionTests` starts a fake provider on a random loopback port and overrides
both the provider URL and API key. It exercises the real Spring AI client and
checks the model, output cap, reasoning setting, authorization with a fake key,
successful reply, empty reply, and 401/429/500 provider errors. It confirms no
retries and that GET/health do not call the provider. Existing tests verify the
AI route is absent without the profile and the trip API still works.

These tests do not prove your real key has access or billing credit. No live
OpenAI request was made during implementation. That is the next manual check.

## Sources checked on 2026-10-03

- [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI OpenAI integration](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [GPT-5.4 mini capabilities and pricing](https://developers.openai.com/api/docs/models/gpt-5.4-mini)
