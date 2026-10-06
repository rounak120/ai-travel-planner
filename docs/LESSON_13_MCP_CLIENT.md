# Lesson 13: calling travel tools through an MCP client

## What changed and why

The previous step exposed our services as MCP tools. This step adds Java code
that speaks MCP and calls them explicitly, before involving a model's decisions.

```text
curl → McpClientController → TravelMcpClient
                                ↓ HTTP /mcp (initialize, then tools/call)
                           TravelTools → DestinationService → Open-Meteo
```

Weather follows the same path through `WeatherService`. The result returns through
these layers. Existing trip/date/location checks still apply. No data is saved;
itinerary generation still calls its services directly.

`McpClientController` gives us ordinary REST URLs for curl. `TravelMcpClient`
validates arguments, initializes an SDK session, lists/calls approved tools and
closes the session with try-with-resources. It never calls travel services directly.
The SDK already comes with the server dependency, so no new dependency is needed.

## Why the connection is opt-in and lazy

`@Profile("mcp-client")` activates these beans only when requested. No new YAML
is needed: this profile only controls bean activation. Enable `mcp` for the server.
Connecting on request avoids connecting during startup before our own web server
is ready. Each request gets a fresh session: simple lifecycle management for this
lesson, not an optimized session pool for high-throughput production workloads.

The client resolves the actual server port (including random test ports), uses
loopback only, and accepts no caller-supplied server URL or arbitrary tool name.
Connection, initialization and request timeouts are bounded. The server-name check
is a sanity check, not authentication. Keep the app bound to loopback.

## Try it

Stop the old app before starting another on port 8080:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -Dspring-boot.run.profiles=ai,mcp,mcp-client spring-boot:run
curl http://localhost:8080/api/v1/mcp/tools
curl 'http://localhost:8080/api/v1/mcp/destinations?name=Jaipur'
curl http://localhost:8080/api/v1/mcp/trips/YOUR_TRIP_ID/weather
```

Weather requires a saved trip, selected destination and dates in the supported
forecast window. Discovery makes no provider calls. Search/weather may call
providers, but none of these endpoints calls Ollama.

Inspect `structuredContent` for data and `isError` for tool success. Missing trips
produce HTTP 200 with `isError: true` and code `NOT_FOUND`: the bridge preserves
MCP tool-result semantics. The original weather REST API retains its HTTP status
behavior. Invalid bridge input returns 400. A failed connection or protocol
exchange returns a sanitized 502, without internal exception details.

Next, separately: let the model choose from a bounded set of tools.

Reference: [Spring AI MCP client documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html).
