package com.example.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

@Provider
public class AppExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(AppExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {
        LOG.error("Request failed: " + e.getMessage(), e);

        if (e instanceof InvalidCurrencyException) {
            return build(Response.Status.BAD_REQUEST, e.getMessage());
        }
        if (e instanceof ConstraintViolationException cve) {
            String msg = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Validation failed");
            return build(Response.Status.BAD_REQUEST, msg);
        }
        if (e instanceof ExternalApiException) {
            return build(Response.Status.BAD_GATEWAY, e.getMessage());
        }

        return build(Response.Status.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private Response build(Response.Status status, String message) {
        return Response.status(status)
                .entity(Map.of(
                        "error", message,
                        "status", status.getStatusCode(),
                        "timestamp", Instant.now().toString()
                ))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
