package io.velocityapi;

import java.lang.reflect.Method;

/**
 * Captures a registered route for runtime binding and OpenAPI generation.
 */
public record RouteDefinition(String httpMethod, String path, Method handlerMethod) {}

