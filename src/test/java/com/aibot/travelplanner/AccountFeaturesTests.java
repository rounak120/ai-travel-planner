package com.aibot.travelplanner;

import com.aibot.travelplanner.ai.ItineraryGenerator;
import com.aibot.travelplanner.itinerary.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test","ai","mcp","mcp-client"})
class AccountFeaturesTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean ChatModel model;
    @MockitoBean ItineraryGenerator generator;
    final JsonMapper json=JsonMapper.builder().build();
    String alice,bob;
    TestAuth.Session a,b;
    final List<UUID> unassigned=new ArrayList<>();
    String base(){return "http://127.0.0.1:"+port;}
    @BeforeEach void accounts() {
        alice="alice_"+UUID.randomUUID().toString().replace("-","");bob="bob_"+UUID.randomUUID().toString().replace("-","");
        for(String user:List.of(alice,bob))jdbc.update("INSERT INTO app_users(username,password_hash) VALUES (?,?)",user,passwords.encode(TestAuth.PASSWORD));
        a=TestAuth.login(base(),alice,TestAuth.PASSWORD);b=TestAuth.login(base(),bob,TestAuth.PASSWORD);
        when(model.getOptions()).thenReturn(org.springframework.ai.ollama.api.OllamaChatOptions.builder().model("qwen3:4b").build());
    }
    @AfterEach void cleanup() {
        unassigned.forEach(id->jdbc.update("DELETE FROM trips WHERE id=?",id));
        jdbc.update("DELETE FROM trips WHERE owner_name IN (?,?)",alice,bob);
        jdbc.update("DELETE FROM app_users WHERE username IN (?,?)",alice,bob);
    }
    HttpResponse<String> call(TestAuth.Session session,String method,String path,Object body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+path));
        if(session!=null)request.header("Cookie",session.cookie()).header(session.header(),session.token());
        if(body!=null)request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        try(var client=HttpClient.newHttpClient()){return client.send(request.build(),HttpResponse.BodyHandlers.ofString());}
    }
    Map<String,Object> trip(String destination) {
        var date=LocalDate.now().plusDays(30).toString();
        return Map.of("destination",destination,"startDate",date,"endDate",date,"travelers",2,"budget","5000.25","currency","INR","interests",List.of("food"));
    }
    String create(TestAuth.Session session) throws Exception {
        var response=call(session,"POST","/api/v1/trips",trip("Jaipur"));assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asText();
    }
    GeneratedItinerary generated(String name) {
        return new GeneratedItinerary(List.of(new GeneratedItinerary.Day(1,List.of(new GeneratedItinerary.Activity(name,"Activity",new BigDecimal("100.00"))))));
    }

    @Test void ownershipProtectsReadsWritesHistoryWeatherChatAndMcp() throws Exception {
        String id=create(a);
        assertThat(call(b,"GET","/api/v1/trips",null).body()).doesNotContain(id);
        for(String suffix:List.of("","/location","/weather","/itinerary","/itinerary/versions","/itinerary/versions/1"))
            assertThat(call(b,"GET","/api/v1/trips/"+id+suffix,null).statusCode()).as(suffix).isEqualTo(404);
        assertThat(call(b,"PUT","/api/v1/trips/"+id,trip("Delhi")).statusCode()).isEqualTo(404);
        assertThat(call(b,"DELETE","/api/v1/trips/"+id,null).statusCode()).isEqualTo(404);
        assertThat(call(b,"POST","/api/v1/trips/"+id+"/itinerary",null).statusCode()).isEqualTo(404);
        assertThat(call(b,"POST","/api/v1/trips/"+id+"/itinerary/revisions",Map.of("versionId",1,"instruction","Cheaper")).statusCode()).isEqualTo(404);
        assertThat(call(b,"POST","/api/v1/ai/chat",Map.of("message","Weather?","tripId",id)).statusCode()).isEqualTo(404);
        var tool=call(b,"GET","/api/v1/mcp/trips/"+id+"/weather",null);
        assertThat(tool.statusCode()).isEqualTo(200);
        assertThat(tool.body()).contains("NOT_FOUND").doesNotContain("Jaipur");
        assertThat(call(a,"GET","/api/v1/trips/"+id,null).statusCode()).isEqualTo(200);
        verifyNoInteractions(generator);verify(model,never()).call(any(Prompt.class));
    }

    @Test void unassignedTripsAreHiddenAndCannotBeClaimed() throws Exception {
        String id=create(a);unassigned.add(UUID.fromString(id));jdbc.update("UPDATE trips SET owner_name=NULL WHERE id=?",UUID.fromString(id));
        for(var session:List.of(a,b)) {
            assertThat(call(session,"GET","/api/v1/trips",null).body()).doesNotContain(id);
            assertThat(call(session,"GET","/api/v1/trips/"+id,null).statusCode()).isEqualTo(404);
            assertThat(call(session,"PUT","/api/v1/trips/"+id,trip("Claim")).statusCode()).isEqualTo(404);
        }
    }

    @Test void updateClearsChangedLocationPreservesSnapshotsAndDeleteCascades() throws Exception {
        String id=create(a);UUID uuid=UUID.fromString(id);
        jdbc.update("INSERT INTO trip_locations(trip_id,document) VALUES (?,CAST('{}' AS jsonb))",uuid);
        jdbc.update("INSERT INTO itineraries(trip_id,document) VALUES (?,CAST('{}' AS jsonb))",uuid);
        var response=call(a,"PUT","/api/v1/trips/"+id,trip("Delhi"));
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("Delhi");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_locations WHERE trip_id=?",Integer.class,uuid)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id=?",Integer.class,uuid)).isEqualTo(1);
        var invalid=new HashMap<>(trip("Delhi"));invalid.put("budget",-1);
        assertThat(call(a,"PUT","/api/v1/trips/"+id,invalid).statusCode()).isEqualTo(400);
        assertThat(call(a,"DELETE","/api/v1/trips/"+id,null).statusCode()).isEqualTo(204);
        assertThat(call(a,"GET","/api/v1/trips/"+id,null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id=?",Integer.class,uuid)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_interests WHERE trip_id=?",Integer.class,uuid)).isZero();
    }

    @Test void revisionUsesSelectedVersionAndPreservesOriginalOnSuccessOrFailure() throws Exception {
        String id=create(a);
        when(generator.generate(any(),any(),any())).thenReturn(generated("Original"));
        assertThat(call(a,"POST","/api/v1/trips/"+id+"/itinerary",null).statusCode()).isEqualTo(200);
        long version=jdbc.queryForObject("SELECT id FROM itineraries WHERE trip_id=?",Long.class,UUID.fromString(id));
        when(generator.revise(any(),any(),any(),any(),eq("Make it cheaper"))).thenReturn(generated("Revised"));
        var result=call(a,"POST","/api/v1/trips/"+id+"/itinerary/revisions",Map.of("versionId",version,"instruction","Make it cheaper"));
        assertThat(result.statusCode()).isEqualTo(200);assertThat(result.body()).contains("Revised");
        assertThat(call(a,"GET","/api/v1/trips/"+id+"/itinerary/versions/"+version,null).body()).contains("Original");
        when(generator.revise(any(),any(),any(),any(),any())).thenReturn(null);
        assertThat(call(a,"POST","/api/v1/trips/"+id+"/itinerary/revisions",Map.of("versionId",version,"instruction","Again")).statusCode()).isEqualTo(502);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id=?",Integer.class,UUID.fromString(id))).isEqualTo(2);
    }

    @Test void memoryIsBoundedScopedAndClearable() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("Answer").build()))));
        for(int i=0;i<5;i++)assertThat(call(a,"POST","/api/v1/ai/chat",Map.of("message","Question "+i)).statusCode()).isEqualTo(200);
        var prompts=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model,times(5)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions()).hasSize(4);
        assertThat(prompts.getValue().getInstructions()).hasSize(8);
        clearInvocations(model);
        call(b,"POST","/api/v1/ai/chat",Map.of("message","Other user"));verify(model).call(prompts.capture());assertThat(prompts.getValue().getInstructions()).hasSize(2);
        clearInvocations(model);
        call(a,"DELETE","/api/v1/ai/chat/memory",null);call(a,"POST","/api/v1/ai/chat",Map.of("message","Fresh"));
        verify(model).call(prompts.capture());assertThat(prompts.getValue().getInstructions()).hasSize(2);
        clearInvocations(model);String id=create(a);
        call(a,"POST","/api/v1/ai/chat",Map.of("message","New scope","tripId",id));verify(model).call(prompts.capture());assertThat(prompts.getValue().getInstructions()).hasSize(2);
    }

    @Test void unauthenticatedAccessCsrfAndLogoutAreEnforced() throws Exception {
        assertThat(call(null,"GET","/api/v1/trips",null).statusCode()).isEqualTo(401);
        assertThat(call(null,"GET","/mcp",null).statusCode()).isEqualTo(401);
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/trips"))
                    .header("Cookie",a.cookie()).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(trip("Blocked")))).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(403);
        }
        assertThat(call(a,"POST","/api/v1/auth/logout",null).statusCode()).isEqualTo(204);
        assertThat(call(a,"GET","/api/v1/trips",null).statusCode()).isEqualTo(401);
    }

    @Test void registrationHashesPasswordsAndLoginRotatesSession() throws Exception {
        String username="new_"+UUID.randomUUID().toString().replace("-","");
        var response=call(null,"GET","/api/v1/auth/csrf",null);
        var token=json.readTree(response.body());
        String cookie=response.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        var anonymous=new TestAuth.Session(cookie,token.get("headerName").asText(),token.get("token").asText());
        try {
            assertThat(call(anonymous,"POST","/api/v1/auth/register",Map.of("username",username,"password","short")).statusCode()).isEqualTo(400);
            assertThat(call(anonymous,"POST","/api/v1/auth/register",Map.of("username",username,"password",TestAuth.PASSWORD)).statusCode()).isEqualTo(201);
            String hash=jdbc.queryForObject("SELECT password_hash FROM app_users WHERE username=?",String.class,username);
            assertThat(hash).isNotEqualTo(TestAuth.PASSWORD);assertThat(passwords.matches(TestAuth.PASSWORD,hash)).isTrue();
            assertThat(call(anonymous,"POST","/api/v1/auth/register",Map.of("username",username,"password",TestAuth.PASSWORD)).statusCode()).isEqualTo(409);
            try(var client=HttpClient.newHttpClient()) {
                var login=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/auth/login"))
                        .header("Cookie",cookie).header(anonymous.header(),anonymous.token())
                        .header("Content-Type","application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("username="+username+"&password="+TestAuth.PASSWORD)).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(login.statusCode()).isEqualTo(204);
                assertThat(login.headers().firstValue("set-cookie").orElseThrow().split(";")[0]).isNotEqualTo(cookie);
            }
            assertThatThrownBy(()->TestAuth.login(base(),username,"incorrect-password")).isInstanceOf(IllegalStateException.class);
        } finally {jdbc.update("DELETE FROM app_users WHERE username=?",username);}
    }
}
