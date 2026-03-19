package io.velocityapi;

/**
 * Used for clean HTTP error responses.
 */
public class HTTPException extends RuntimeException {
    private final int statusCode;

    public HTTPException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}

