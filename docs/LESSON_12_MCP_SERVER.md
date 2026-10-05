# Publish our services as read-only MCP tools

We already have REST endpoints for destination search and trip weather. This
step adds another interface to those same Java services: an MCP server. It lets
an MCP client discover tool names and input schemas before calling them.

## How this differs from REST

Our REST caller already knows URLs such as `/api/v1/destinations?name=Jaipur`.
An MCP caller talks to `/mcp`, initializes a protocol session, asks `tools/list`,
and calls `tools/call` with a discovered name and arguments. Spring AI's MCP
starter handles that protocol; our `TravelTools` methods adapt calls to the
existing services. This is a real MCP transport, not a REST route named MCP.

MCP client → Streamable HTTP `/mcp` → TravelTools → DestinationService or
WeatherService → existing provider/database logic → MCP result.

| Tool | Input | What it does |
| --- | --- | --- |
| search_destinations | name | Returns up to five candidate locations and attribution; saves nothing |
| get_trip_weather | tripId | Reads the trip/selection and fetches a fresh forecast for its dates; saves nothing |

Both tools have read-only hints and declare that they access external data.
Those hints describe behavior; they are not an authorization mechanism. The
weather tool retains the standalone weather endpoint's errors, rather than the
planner's fallback behavior. No trip-write or booking tools are registered.

## Why the mcp profile?

Adding the starter could otherwise expose its endpoint by default. We explicitly
disable the server/scanner in `application.yml`; `application-mcp.yml` enables
them. `TravelTools` and `LocalMcpFilter` also require that profile. It works without
the `ai` profile because no model is involved in serving these tools.

Only annotated MCP tools are registered. Automatic conversion of other Spring AI
tool callbacks is disabled. Resources, prompts and completion capabilities are
disabled because this milestone implements only tools. Tests assert exactly two
discoverable tools and an absent endpoint without the profile.

## Errors and data

Success returns structured JSON and a text JSON representation for compatibility.
Both include the same source attribution and warnings as the REST services.
Expected input/provider errors return a tool result with `isError: true`, a safe
message and a code such as INVALID_ARGUMENT, NOT_FOUND, UNSUPPORTED or
PROVIDER_UNAVAILABLE. Unexpected service errors get a generic TOOL_UNAVAILABLE.
Unknown tools/protocol errors are handled at the MCP layer.

Initialization, discovery and ping do not call providers. Each actual tool call
may use external quota. There is no additional retry or AI call in this adapter.

## Start it

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai,mcp
```

Use just `mcp` if you do not need AI endpoints. Stop any app already on port 8080.
The address remains `127.0.0.1`; do not change it to a public bind.

## Test the protocol manually

An MCP client normally performs these steps for you. To see them with curl:

```bash
curl -i --max-time 15 http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual-test","version":"1.0"}}}'
```

Copy the response's `Mcp-Session-Id` header and negotiated `protocolVersion` into
these shell variables. Do not use the literal placeholders:

```bash
MCP_SESSION_ID='REPLACE_WITH_SESSION_ID'
MCP_PROTOCOL_VERSION='2025-06-18'

curl --max-time 15 http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" -H "MCP-Protocol-Version: $MCP_PROTOCOL_VERSION" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl --max-time 15 http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" -H "MCP-Protocol-Version: $MCP_PROTOCOL_VERSION" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

curl --max-time 15 http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" -H "MCP-Protocol-Version: $MCP_PROTOCOL_VERSION" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_destinations","arguments":{"name":"Jaipur"}}}'

# Ends only this protocol session; it does NOT delete trips or itineraries.
curl --max-time 15 -X DELETE http://127.0.0.1:8080/mcp \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" -H "MCP-Protocol-Version: $MCP_PROTOCOL_VERSION"
```

Some responses are SSE-formatted (`event:`/`data:` lines), which is normal for
Streamable HTTP. The tested protocol version was 2025-06-18. Session IDs are
temporary protocol state, not account authentication. See `requests-mcp.http`
for a weather call example too.

## Security and what's next

The starter is unauthenticated. Any local process that can reach it can invoke
registered tools. `LocalMcpFilter` rejects non-local Host names and supplied
Origins that differ from the endpoint's HTTP origin. Native clients may omit
Origin. This is a local-development guard, not user authentication or ownership.
Do not expose it through tunnels or proxies/public hosting without proper auth,
ownership checks and provider rate limiting.

The existing itinerary generator is unchanged: it still explicitly calls weather
services then Ollama. We have NOT installed a desktop client, connected an AI
agent automatically, or let the model choose tools. The next MCP milestone can
add a bounded client workflow, separately from this server.

References: [Spring AI MCP server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html),
[Streamable HTTP setup](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html).
