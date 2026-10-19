package com.aibot.travelplanner;

import com.aibot.travelplanner.destination.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LocatedTripCreationTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DestinationService destinations;
    @MockitoSpyBean TripLocationStore locations;
    final JsonMapper json=JsonMapper.builder().build();
    UUID created;

    @BeforeEach void setup() {
        when(destinations.search("Jaipur")).thenReturn(new DestinationSearch(List.of(
                new DestinationSearch.Place(123,"Jaipur","India","IN","Rajasthan",26.9,75.8,"Asia/Kolkata")),
                "Open-Meteo","https://open-meteo.com/"));
    }
    @AfterEach void cleanup() {if(created!=null)jdbc.update("DELETE FROM trips WHERE id=?",created);}
    Map<String,Object> body(long placeId) {
        var date=LocalDate.now().plusDays(3).toString();
        return Map.of("trip",Map.of("destination","Untrusted label","startDate",date,"endDate",date,
                "travelers",2,"budget","5000.25","currency","INR","interests",List.of("food")),
                "location",Map.of("searchName","Jaipur","placeId",placeId));
    }
    int count(){return jdbc.queryForObject("SELECT count(*) FROM trips",Integer.class);}
    HttpResponse<String> post(Object body) throws Exception {
        var request=TestAuth.request(URI.create("http://127.0.0.1:"+port+"/api/v1/trips/with-location"))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        try(var client=HttpClient.newHttpClient()){return client.send(request,HttpResponse.BodyHandlers.ofString());}
    }
    @Test void savesCanonicalTripAndLocationTogether() throws Exception {
        var response=post(body(123));assertThat(response.statusCode()).isEqualTo(201);
        var trip=json.readTree(response.body());created=UUID.fromString(trip.get("id").asText());
        assertThat(trip.get("destination").asText()).isEqualTo("Jaipur");
        assertThat(locations.find(created).orElseThrow().place().timezone()).isEqualTo("Asia/Kolkata");
        assertThat(jdbc.queryForObject("SELECT owner_name FROM trips WHERE id=?",String.class,created)).isEqualTo("test_fixture");
    }
    @Test void staleSelectionDoesNotCreateTrip() throws Exception {
        int before=count();assertThat(post(body(999)).statusCode()).isEqualTo(422);assertThat(count()).isEqualTo(before);
    }
    @Test void unavailableProviderDoesNotCreateTrip() throws Exception {
        when(destinations.search("Jaipur")).thenThrow(new DestinationUnavailableException());
        int before=count();assertThat(post(body(123)).statusCode()).isEqualTo(502);assertThat(count()).isEqualTo(before);
    }
    @Test void missingSelectionIsRejectedBeforeLookup() throws Exception {
        assertThat(post(Map.of("trip",body(123).get("trip"))).statusCode()).isEqualTo(400);
        verifyNoInteractions(destinations);
    }
    @Test void storageFailureRollsBackTrip() throws Exception {
        doThrow(new IllegalStateException("Simulated location failure")).when(locations).save(any());
        int before=count();assertThat(post(body(123)).statusCode()).isEqualTo(500);assertThat(count()).isEqualTo(before);
    }
}
