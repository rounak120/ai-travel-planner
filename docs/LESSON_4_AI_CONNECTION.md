# Lesson 4, step 1 — connect a local model with Ollama

This step checks one thing: can our Spring Boot app send a short prompt through
Spring AI and return the model's reply? It does not generate or save an itinerary.
The existing trip API, database tables, and migration are unchanged.

## How the request moves through this project

`POST /api/v1/ai/test` → `AiConnectionController` → `AiConnectionService`
→ Spring AI `ChatClient` → Ollama on port 11434 → Qwen3 → JSON response.

- The controller owns HTTP: route, response shape, and a safe 502 error response.
- The service owns the prompt and model call. It uses a fixed prompt, so this
  test sends no trip records. Inference runs on this Mac, not a paid cloud API.
- Spring AI handles the provider HTTP request and response parsing. The
  service uses its `ChatClient` interface rather than assembling HTTP manually.
- PostgreSQL is not involved in this particular request; the rest of the
  application still uses PostgreSQL as before.

## Why we added these settings

`pom.xml` imports the Spring AI 2.0.1 BOM (a list of compatible library versions)
and the Ollama starter. Spring Boot remains 4.1.1 and Java remains 21.

`application.yml` disables AI models by default. `application-ai.yml` enables
only chat when the `ai` profile is active. The controller and service have
`@Profile("ai")` for the same reason: plain trip development should not require
Ollama to be running. Embeddings, image, audio, and moderation clients remain disabled.

The AI settings select `qwen3:4b`, connect to `http://127.0.0.1:11434`, cap output
at 512 tokens (including reasoning), and set a 4096-token context window to bound memory use. The
current `qwen3:4b` tag is a thinking variant, so we enable its thinking mode:
Ollama separates reasoning from final answer text, and our service returns only
the answer. Disabling thinking on this variant caused reasoning text to appear
in the answer during the live smoke test. A 128-token cap also sometimes expired
before the answer began, so the local budget is 512. HTTP settings allow 5 seconds to connect and
120 seconds to read a response, accommodating a cold model load. These limits
are for a connection test, not a full itinerary.

Automatic model downloads are disabled: we explicitly pull the model once.
Automatic request retries are disabled too. In the pinned Spring AI 2.0.1,
`spring.ai.retry.max-attempts: 0` means zero retries after the initial attempt;
our tests verify the actual request count.

Ollama is the runtime/server, not the model. Qwen3 4B is the downloaded model
it runs. It is a small starting choice for this M1 Pro/32 GB Mac; we will assess
its itinerary quality later. Larger models can improve quality at the cost of
more memory, download size, and latency. Local inference has no per-request API
fee, but uses your hardware, disk space, and electricity.

`OLLAMA_MODEL` and `OLLAMA_BASE_URL` can override the defaults in `.env`.
No API key is needed. The former `OPENAI_API_KEY` entry, if present, is unused;
the OpenAI starter has been removed. Keep any old key private, and revoke it in
the OpenAI dashboard if you no longer want it. We have not revoked it for you.

## Start and test manually

Install Ollama once on this Mac (already done during setup):

```bash
brew install ollama
```

Start the local server in a separate terminal. These flags disable cloud
features and bind only to this computer:

```bash
OLLAMA_NO_CLOUD=1 OLLAMA_HOST=127.0.0.1:11434 ollama serve
```

The server is not registered to start at login; rerun this after a reboot.
Models are stored under your user account's `.ollama/models`, not in Git.
Download the model once (about 2.5 GB; already done during setup):

```bash
ollama pull qwen3:4b
ollama list
```

Keep PostgreSQL running and your existing database settings in `.env`.
Stop the old application before reusing port 8080. From the project root:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai
```

Startup and `/actuator/health` never generate text. To invoke the local model:

```bash
curl -i -X POST http://localhost:8080/api/v1/ai/test
```

An illustrative successful response is:

```json
{"message":"Travel planner AI connection is working."}
```

The text comes from the model, not a hard-coded success response. A 502 means
the provider call failed or returned no text. Check `ollama list`, that the
Ollama server is running, and that its URL/model match the app settings. Raw provider errors
are deliberately not returned or logged by our service because they can contain
sensitive details. A 404 means the `ai` profile is not active; GET returns 405
when the profile is active. Restart after changing `.env`.

Do not publish this unauthenticated endpoint. Each POST consumes local compute;
the application remains bound to loopback. Authentication and rate limits are
required before shared deployment.

## What our automated tests prove

`AiConnectionTests` starts a fake provider on a random loopback port and overrides
the provider URL. It exercises the real Spring AI client and
checks the model, output/context caps, separate thinking, absence of authorization,
successful reply, empty reply, and 404/429/500 provider errors. It confirms no
retries and that GET/health do not call the provider. Existing tests verify the
AI route is absent without the profile and the trip API still works.

Automated tests do not require the Ollama daemon or downloaded model. A separate
manual smoke test checks the real model through the same Spring Boot endpoint.
See `VERIFICATION.md` for the actual results.

## Sources checked on 2026-10-03

- [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI Ollama integration](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
- [Ollama local-only mode and storage](https://docs.ollama.com/faq)
- [Qwen3 4B model](https://ollama.com/library/qwen3:4b)
