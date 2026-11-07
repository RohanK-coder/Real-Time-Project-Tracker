package com.example.tracker.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Minimal JSON HTTP client that never throws on 4xx, so tests can assert on status codes. */
public class Api {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    private final ObjectMapper mapper;

    public Api(String base, ObjectMapper mapper) {
        this.base = base;
        this.mapper = mapper;
    }

    public Response get(String path, String token) { return send("GET", path, token, null); }
    public Response post(String path, String token, Object body) { return send("POST", path, token, body); }
    public Response put(String path, String token, Object body) { return send("PUT", path, token, body); }
    public Response delete(String path, String token) { return send("DELETE", path, token, null); }

    public Response send(String method, String path, String token, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json");
            if (token != null) builder.header("Authorization", "Bearer " + token);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            HttpResponse<String> res = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = res.body() == null || res.body().isBlank() ? NullNode.getInstance() : mapper.readTree(res.body());
            return new Response(res.statusCode(), json);
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " failed", e);
        }
    }

    public record Response(int status, JsonNode body) {
        public JsonNode expect(int expected) {
            assertThat(status).as("HTTP status, body=" + body).isEqualTo(expected);
            return body;
        }
    }
}
