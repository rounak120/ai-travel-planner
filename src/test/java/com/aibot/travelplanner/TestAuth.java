package com.aibot.travelplanner;

import java.net.*;
import java.net.http.*;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.json.JsonMapper;

public final class TestAuth {
    public static final String PASSWORD="fixture-password-only-2026";
    private static final ConcurrentHashMap<String, Session> sessions=new ConcurrentHashMap<>();
    public record Session(String cookie,String header,String token) {}
    public static HttpRequest.Builder request(URI uri) {
        var session=sessions.computeIfAbsent(uri.getScheme()+"://"+uri.getAuthority(),base->login(base,"test_fixture",PASSWORD));
        return HttpRequest.newBuilder(uri).header("Cookie",session.cookie()).header(session.header(),session.token());
    }
    public static Session login(String base,String username,String password) {
        try(var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build()) {
            var json=JsonMapper.builder().build();
            var token=json.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            var response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/login"))
                .header(token.get("headerName").asText(),token.get("token").asText())
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(username,java.nio.charset.StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,java.nio.charset.StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=204)throw new IllegalStateException("Fixture login failed: "+response.statusCode());
            token=json.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            var cookie=((CookieManager)client.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
                .filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow();
            return new Session("JSESSIONID="+cookie.getValue(),token.get("headerName").asText(),token.get("token").asText());
        } catch(Exception exception) {throw new IllegalStateException(exception);}
    }
}
