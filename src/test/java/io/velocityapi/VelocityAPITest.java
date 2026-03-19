package io.velocityapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.velocityapi.example.UserController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VelocityAPITest {
    private static VelocityAPI api;
    private static int port;

    private static HttpClient client;
    private static ObjectMapper objectMapper;
    private static URI baseUri;

    @BeforeAll
    public static void beforeAll() throws Exception {
        api = new VelocityAPI();
        api.register(new UserController());

        // Start on random port to avoid collisions.
        api.start(0);
        port = api.port();

        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

        objectMapper = new ObjectMapper();
        baseUri = URI.create("http://localhost:" + port + "/api/");

        // Give Vert.x time to settle for the first request.
        Thread.sleep(300);
    }

    @AfterAll
    public static void afterAll() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    public void getUsers_returnsJsonArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        UserController.User[] users = objectMapper.readValue(resp.body(), UserController.User[].class);
        assertTrue(users.length >= 3);
    }

    @Test
    public void getUser_existingId_returnsUser() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users/1"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        UserController.User user = objectMapper.readValue(resp.body(), UserController.User.class);
        assertEquals(1, user.id());
        assertNotNull(user.name());
        assertNotNull(user.email());
    }

    @Test
    public void getUser_missingId_returnsErrorMap() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users/999"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());

        Map<String, Object> err = objectMapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals(404, err.get("status"));
        assertEquals("User not found", err.get("detail"));
    }

    @Test
    public void postUser_assignsId_andReturnsCreatedUser() throws Exception {
        Map<String, Object> input = Map.of(
            "name", "Velocity",
            "email", "velocity@example.com",
            "age", 25
        );

        String json = objectMapper.writeValueAsString(input);

        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users"))
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        UserController.User created = objectMapper.readValue(resp.body(), UserController.User.class);
        assertTrue(created.id() > 0);
        assertEquals("Velocity", created.name());
        assertEquals("velocity@example.com", created.email());
    }

    @Test
    public void searchUsers_filtersByQueryParam() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users/search?name=arj"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        UserController.User[] users = objectMapper.readValue(resp.body(), UserController.User[].class);
        for (UserController.User u : users) {
            assertNotNull(u.name());
            assertTrue(u.name().toLowerCase().contains("arj"));
        }
    }

    @Test
    public void validation_passes_onValidPostBody() throws Exception {
        Map<String, Object> input = Map.of(
            "name", "Arjuna",
            "email", "a@b.com",
            "age", 25
        );

        String json = objectMapper.writeValueAsString(input);

        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users"))
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        UserController.User created = objectMapper.readValue(resp.body(), UserController.User.class);
        assertTrue(created.id() > 0);
    }

    @Test
    public void validation_fails_onBlankName() throws Exception {
        Map<String, Object> input = Map.of(
            "name", "",
            "email", "a@b.com",
            "age", 25
        );

        String json = objectMapper.writeValueAsString(input);

        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users"))
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, resp.statusCode());

        Map<String, Object> body = objectMapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals(422, body.get("status"));

        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertTrue(errors.stream().anyMatch(e -> "name".equals(e.get("field"))));
    }

    @Test
    public void validation_fails_onInvalidEmail() throws Exception {
        Map<String, Object> input = Map.of(
            "name", "Test",
            "email", "notanemail",
            "age", 25
        );

        String json = objectMapper.writeValueAsString(input);

        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users"))
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, resp.statusCode());

        Map<String, Object> body = objectMapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals(422, body.get("status"));

        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertTrue(errors.stream().anyMatch(e -> "email".equals(e.get("field"))));
    }

    @Test
    public void httpException_returnsCorrectStatusFields() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("users/999"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());

        Map<String, Object> body = objectMapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals(404, body.get("status"));
        assertEquals("User not found", body.get("detail"));
    }
}

