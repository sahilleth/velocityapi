package io.velocityapi.example;

import io.velocityapi.HTTPException;
import io.velocityapi.annotations.Body;
import io.velocityapi.annotations.Controller;
import io.velocityapi.annotations.DELETE;
import io.velocityapi.annotations.Email;
import io.velocityapi.annotations.GET;
import io.velocityapi.annotations.Max;
import io.velocityapi.annotations.Min;
import io.velocityapi.annotations.NotBlank;
import io.velocityapi.annotations.POST;
import io.velocityapi.annotations.PUT;
import io.velocityapi.annotations.Param;
import io.velocityapi.annotations.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller("/api")
public class UserController {
    public record User(int id, String name, String email) {}

    public static class CreateUserRequest {
        @NotBlank
        public String name;

        @Email
        @NotBlank
        public String email;

        @Min(13)
        @Max(120)
        public int age;
    }

    private final Map<Integer, User> users = new HashMap<>();
    private int nextId = 4;

    public UserController() {
        users.put(1, new User(1, "Arjun", "arjun@example.com"));
        users.put(2, new User(2, "Maria", "maria@example.com"));
        users.put(3, new User(3, "Arjita", "arjita@example.com"));
    }

    @GET("/users")
    public List<User> listUsers() {
        return new ArrayList<>(users.values());
    }

    @GET("/users/{id}")
    public User getUser(@Param("id") int id) {
        User found = users.get(id);
        if (found == null) {
            throw new HTTPException(404, "User not found");
        }
        return found;
    }

    @POST("/users")
    public User postUser(@Body CreateUserRequest body) {
        int assignedId = nextId++;
        User created = new User(assignedId, body.name, body.email);
        users.put(assignedId, created);
        return created;
    }

    @PUT("/users/{id}")
    public User putUser(@Param("id") int id, @Body CreateUserRequest body) {
        if (!users.containsKey(id)) {
            throw new HTTPException(404, "User not found");
        }
        User updated = new User(id, body.name, body.email);
        users.put(id, updated);
        return updated;
    }

    @DELETE("/users/{id}")
    public Map<String, Object> deleteUser(@Param("id") int id) {
        User removed = users.remove(id);
        if (removed == null) {
            throw new HTTPException(404, "User not found");
        }
        return Map.of("deleted", true, "id", id);
    }

    @GET("/users/search")
    public List<User> search(@Query("name") String name) {
        if (name == null) {
            return new ArrayList<>(users.values());
        }

        String needle = name.toLowerCase(Locale.ROOT);
        return users.values().stream()
            .filter(u -> u.name.toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }
}

