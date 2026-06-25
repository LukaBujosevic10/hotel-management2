package com.hotel.room.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised error handling.
 *
 * Every response carries a message a human can act on. Instead of the useless
 * "Validation failed", the caller gets exactly which field is wrong and why,
 * e.g. "Check-out date must be after the check-in date."
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------------------------------------------------------- 404 / 409

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "This operation conflicts with data that already exists. "
                        + "Most likely a value that must be unique is already used.", req, null);
    }

    // ------------------------------------------------------------ 400 (bean validation)

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        // Human readable summary: "Check-in date is required. Number of guests must be at least 1."
        String message = fieldErrors.entrySet().stream()
                .map(e -> sentence(humanize(e.getKey()) + " " + lowerFirst(e.getValue())))
                .collect(Collectors.joining(" "));
        if (message.isBlank()) {
            message = "The submitted data is not valid.";
        }
        return build(HttpStatus.BAD_REQUEST, message, req, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String path = cv.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.putIfAbsent(field, cv.getMessage());
        }
        String message = fieldErrors.entrySet().stream()
                .map(e -> sentence(humanize(e.getKey()) + " " + lowerFirst(e.getValue())))
                .collect(Collectors.joining(" "));
        return build(HttpStatus.BAD_REQUEST, message, req, fieldErrors);
    }

    // ------------------------------------------------------------ 400 (bad input shape)

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing.", req,
                Map.of(ex.getParameterName(), "is required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        String name = ex.getName();
        Object value = ex.getValue();
        Class<?> target = ex.getRequiredType();
        String message;
        if (target != null && target.isEnum()) {
            message = "'" + value + "' is not a valid value for '" + name + "'. Allowed values: "
                    + allowedValues(target) + ".";
        } else if (target != null && target.getSimpleName().equals("LocalDate")) {
            message = "'" + value + "' is not a valid date for '" + name + "'. Expected format is yyyy-MM-dd.";
        } else {
            message = "'" + value + "' is not a valid value for '" + name + "'.";
        }
        return build(HttpStatus.BAD_REQUEST, message, req, Map.of(name, "has an invalid value"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "value"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            Class<?> target = ife.getTargetType();
            String message;
            if (target != null && target.isEnum()) {
                message = "'" + ife.getValue() + "' is not a valid value for " + humanize(field).toLowerCase()
                        + ". Allowed values: " + allowedValues(target) + ".";
            } else if (target != null && target.getSimpleName().equals("LocalDate")) {
                message = "'" + ife.getValue() + "' is not a valid date for " + humanize(field).toLowerCase()
                        + ". Expected format is yyyy-MM-dd.";
            } else {
                message = "'" + ife.getValue() + "' is not a valid value for " + humanize(field).toLowerCase() + ".";
            }
            return build(HttpStatus.BAD_REQUEST, message, req, Map.of(field, "has an invalid value"));
        }
        return build(HttpStatus.BAD_REQUEST,
                "The request body could not be read. Please check that it is valid JSON.", req, null);
    }

    // ---------------------------------------------------------------- fallback

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error: " + ex.getMessage(), req, null);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest req, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), message, req.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private static String allowedValues(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        return constants == null ? "" : Arrays.stream(constants).map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    /** checkInDate -> "Check in date" */
    private static String humanize(String field) {
        String spaced = field.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) return "is not valid";
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String sentence(String text) {
        String trimmed = text.trim();
        return trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?") ? trimmed : trimmed + ".";
    }
}
