package com.aibot.travelplanner.weather;

import com.aibot.travelplanner.destination.DestinationSearch;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeatherClient {
    private final HttpClient http;
    private final JsonMapper json;
    private final String baseUrl;
    private final Duration timeout;

    public WeatherClient(JsonMapper json,
            @Value("${travel.weather.base-url:https://api.open-meteo.com}") String baseUrl,
            @Value("${travel.weather.timeout-ms:5000}") long timeoutMs) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public List<TripWeather.Day> forecast(DestinationSearch.Place place, LocalDate start, LocalDate end) {
        try {
            var uri = URI.create(baseUrl + "/v1/forecast?latitude=" + place.latitude() + "&longitude=" + place.longitude()
                    + "&timezone=" + URLEncoder.encode(place.timezone(), StandardCharsets.UTF_8)
                    + "&start_date=" + start + "&end_date=" + end
                    + "&daily=temperature_2m_min,temperature_2m_max,precipitation_sum"
                    + "&temperature_unit=celsius&precipitation_unit=mm&timeformat=iso8601");
            var response = http.send(HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new WeatherUnavailableException();
            var root = json.readTree(response.body());
            if (root == null || !root.isObject() || root.path("error").asBoolean(false)) throw new WeatherUnavailableException();
            var units = root.path("daily_units");
            if (!"°C".equals(units.path("temperature_2m_min").asText())
                    || !"°C".equals(units.path("temperature_2m_max").asText())
                    || !"mm".equals(units.path("precipitation_sum").asText())
                    || !"iso8601".equals(units.path("time").asText())) throw new WeatherUnavailableException();
            // Providers may normalize timezone aliases (e.g. Asia/Kolkata to Asia/Calcutta).
            if (!ZoneId.of(root.path("timezone").asText()).getRules().equals(ZoneId.of(place.timezone()).getRules())) {
                throw new WeatherUnavailableException();
            }
            int count = Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);
            var daily = root.path("daily");
            for (String key : List.of("time", "temperature_2m_min", "temperature_2m_max", "precipitation_sum")) {
                if (!daily.path(key).isArray() || daily.path(key).size() != count) throw new WeatherUnavailableException();
            }
            var days = new ArrayList<TripWeather.Day>();
            for (int i = 0; i < count; i++) {
                var date = LocalDate.parse(daily.path("time").get(i).asText());
                if (!date.equals(start.plusDays(i))) throw new WeatherUnavailableException();
                Double min = number(daily.path("temperature_2m_min").get(i));
                Double max = number(daily.path("temperature_2m_max").get(i));
                Double rain = number(daily.path("precipitation_sum").get(i));
                if ((min != null && max != null && min > max) || (rain != null && rain < 0)) throw new WeatherUnavailableException();
                days.add(new TripWeather.Day(date, min, max, rain));
            }
            return days;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WeatherUnavailableException();
        } catch (Exception exception) {
            throw new WeatherUnavailableException();
        }
    }

    private Double number(JsonNode node) {
        if (node.isNull()) return null; // Unavailable model data is not zero rainfall/temperature.
        if (!node.isNumber() || !Double.isFinite(node.asDouble())) throw new WeatherUnavailableException();
        return node.asDouble();
    }

    @PreDestroy
    public void close() { http.close(); }
}
