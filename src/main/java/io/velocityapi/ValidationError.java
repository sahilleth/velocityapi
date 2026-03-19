package io.velocityapi;

/**
 * Field-level validation failure (FastAPI-style).
 */
public record ValidationError(String field, String message) {}

