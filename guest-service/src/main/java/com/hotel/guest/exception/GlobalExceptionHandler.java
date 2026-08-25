package com.hotel.guest.exception;

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

// Jedinstven format greske za sve endpoint-e ovog servisa (vidi ApiError).
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        // najcesce email/documentId koji vec postoji, a stiglo je mimo naseg existsBy provere (race)
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "Ova promena se kosi sa vec postojecim podatkom (verovatno email ili broj dokumenta).", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, joinFieldErrors(fieldErrors), req, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String path = cv.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.putIfAbsent(field, cv.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, joinFieldErrors(fieldErrors), req, fieldErrors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Nedostaje parametar '" + ex.getParameterName() + "'.", req,
                Map.of(ex.getParameterName(), "is required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        Class<?> target = ex.getRequiredType();
        String message = target != null && target.isEnum()
                ? "'" + ex.getValue() + "' nije validna vrednost za '" + ex.getName() + "'. Dozvoljeno: "
                        + allowedValues(target) + "."
                : "'" + ex.getValue() + "' nije validna vrednost za '" + ex.getName() + "'.";
        return build(HttpStatus.BAD_REQUEST, message, req, Map.of(ex.getName(), "has an invalid value"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "value"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            Class<?> target = ife.getTargetType();
            String message = target != null && target.isEnum()
                    ? "'" + ife.getValue() + "' nije validna vrednost za " + humanize(field).toLowerCase()
                            + ". Dozvoljeno: " + allowedValues(target) + "."
                    : "'" + ife.getValue() + "' nije validna vrednost za " + humanize(field).toLowerCase() + ".";
            return build(HttpStatus.BAD_REQUEST, message, req, Map.of(field, "has an invalid value"));
        }
        return build(HttpStatus.BAD_REQUEST, "Telo zahteva nije validan JSON.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error: " + ex.getMessage(), req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest req, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), message, req.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    // Spaja field errors u jednu recenicu tipa "Email is required. Phone must match ...".
    private static String joinFieldErrors(Map<String, String> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            return "The submitted data is not valid.";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fieldErrors.entrySet()) {
            String piece = humanize(e.getKey()) + " " + lowerFirst(e.getValue());
            sb.append(sentence(piece)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String allowedValues(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        return constants == null ? "" : Arrays.stream(constants).map(Object::toString)
                .collect(Collectors.joining(", "));
    }

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
