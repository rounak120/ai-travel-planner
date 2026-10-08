# Lesson 14: Ollama chooses read-only MCP tools

## What this adds to our project

`POST /api/v1/ai/chat` accepts a message and an optional `tripId`. It returns the
AI answer plus a `tools` list containing the actual MCP results. It does not save
chat history, change a trip, select a location or create an itinerary.

```text
User message → TravelAssistantController → TravelAssistantService → Ollama
                                                  ↑                  ↓ tool request
                                                  └─ MCP result ← TravelMcpClient
                                                                      ↓ HTTP /mcp
                                                                  TravelTools
                                                                      ↓
                                                               Travel services
```

Previously Java always picked the tool. Now Ollama sees descriptions and JSON
argument schemas and may return a structured tool request instead of an answer.
Java validates that request, executes it through our existing MCP client, and
sends the result back to Ollama for an explanation. Ollama has no direct database
or provider access.

## Why an explicit loop

Spring AI 2's low-level `ChatModel` returns requested tool calls without executing
them. We use this to keep the rules visible in `TravelAssistantService`: at most
two tool calls and three model rounds per HTTP request. Unknown tools, malformed
arguments, incomplete answers or requests beyond the budget produce a safe 502.
Tool/provider errors returned by MCP retain `isError: true` and are supplied to
the model so it can explain the limitation. Individual model calls retain the
configured HTTP timeout; this is a count limit, not one overall wall-clock deadline.

Only `search_destinations(name)` is always offered. `get_trip_weather()` is offered
only when the HTTP request includes a valid full UUID. Its model-facing schema has
no arguments: Java supplies the request's trip ID to MCP. This prevents the model
from inventing or switching IDs. It is not authorization—any local caller can
still supply a known trip ID. Authentication and ownership checks remain future work.

Tool schemas are deliberately defined here rather than dynamically exposing every
discovered MCP tool. The definition-only callbacks cannot execute tools themselves;
the explicit switch is the only execution path. Tools are never registered globally,
so itinerary generation and the AI connection test stay unchanged.

Our runtime options explicitly retain the configured model. A standalone Ollama
options builder otherwise defaults to `mistral`, even though this project uses
`qwen3:4b`. Native thinking separation keeps this Qwen tag's reasoning out of the
answer text; only the answer and tool evidence are returned. Output (including
thinking) is capped at 2,048 tokens per round and context at 8,192 tokens.

## Test it

Run with all three profiles (stop an older process on port 8080 first):

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -Dspring-boot.run.profiles=ai,mcp,mcp-client spring-boot:run
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Search for Jaipur and tell me the country and region of the Indian result."}'
```

Weather example—replace the UUID with your saved trip:

```bash
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Check the forecast for this trip.","tripId":"YOUR_TRIP_ID"}'
```

Weather still requires a selected location and supported dates. Old October 6–7
demo trips are now in the past, so they should return an unsupported-date tool
error, not a fabricated forecast. No new demo trip is created automatically.

Read `message` as AI-generated prose and `tools[].result.structuredContent` as the
actual source data. Empty `tools` means no tool ran; a fluent answer is not proof
of a lookup. The prompt asks for faithful attribution and honest errors, but does
not guarantee the model will follow those instructions. Tool outputs are treated
as data, not instructions; our hard safety boundary is the read-only allowlist,
strict arguments, fixed trip binding and call budget.

The endpoint requires all three profiles and remains local/unauthenticated.
Input messages are limited to 2,000 characters. Invalid input returns 400; model
or orchestration failures return sanitized 502. Nothing is automatically retried.

Reference: [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html).
