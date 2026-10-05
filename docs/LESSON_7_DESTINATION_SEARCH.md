# First external travel-data integration: destination search

Our trip currently stores a destination as text. A name such as Paris can match
multiple places. Before adding weather, we need a way to discover the intended
location and its latitude/longitude. This milestone exposes that lookup; it does
not automatically choose or persist a match.

## How it works in this project

`GET /api/v1/destinations?name=Jaipur` → `DestinationController` →
`DestinationService` → Open-Meteo geocoding → validated `DestinationSearch` response.

The controller trims the query and rejects missing, too-short, too-long or
control-character input. The service URL-encodes it as a single parameter and
uses a configured provider URL, not a URL supplied by the caller. Count is fixed
at five and output language at English. No database reads/writes or AI calls are
part of this flow; only the search text is sent outside your computer.

We use Java's HTTP client, already available in Java 21, so no new dependency is
needed. Unlike model inference, this small lookup should finish quickly: it has
a five-second connection/request timeout, no application retries and no redirect
following. Provider errors, invalid JSON and invalid required location fields
become a safe 502 response rather than exposing the provider's body.

## Why our own response record?

`DestinationSearch.Place` exposes just the fields our project needs: provider ID,
name, country/code, region, coordinates and timezone. Provider-only details like
population and administrative IDs are omitted. Optional fields can be null, while
IDs, names and coordinates must be valid. An empty search returns an empty array,
not a provider error. Attribution and source URL accompany every successful result.

## Why not connect this straight to the model?

We first need a reliable, independently testable external-data client. Later we
can let the traveler select a location, store coordinates, and use them for a
weather request. An MCP or AI tool adapter could reuse this integration later;
this milestone itself does not implement MCP or model tool calling.

Geocoding does not verify activities in existing AI drafts. It is not an
attraction search, travel availability check, or weather forecast. Search results
should be reviewed before selecting a destination.

## Try it

```bash
curl --get http://localhost:8080/api/v1/destinations --data-urlencode 'name=Jaipur'
```

Tests use a local fake provider, including slow responses and malformed data;
they do not rely on the public service or consume its quota. Config keys are
`travel.geocoding.base-url` and `travel.geocoding.timeout-ms` in `application.yml`.
Only trusted local configuration should override the provider URL.

## Provider terms and attribution

As checked October 5, 2026, the free service is restricted to non-commercial use,
with limits of fewer than 10,000 calls/day, 5,000/hour and 600/minute. No API key
is needed for this non-commercial endpoint. It is not an unlimited free service.
The project has no caching or rate limiter yet; do not expose it publicly. Keep
the returned Open-Meteo/GeoNames attribution when displaying results and review
licensing before commercial deployment. No account or subscription was created.

Sources: [Geocoding documentation](https://open-meteo.com/en/docs/geocoding-api),
[Open-Meteo terms](https://open-meteo.com/en/terms),
[GeoNames](https://www.geonames.org/).
