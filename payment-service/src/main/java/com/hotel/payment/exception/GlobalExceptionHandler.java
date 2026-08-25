package com.hotel.payment.exception;

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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ResourceNotFoundException.class})
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateResourceException ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest req) {
        // koristi se npr. kad se pokusa naplatiti vec naplaceno placanje
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrityViolation(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "Duplikat placanja za istu rezervaciju.", req, null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, summarize(fieldErrors), req, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> pathValidation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String path = cv.getPropertyPath().toString();
            fieldErrors.putIfAbsent(path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path, cv.getMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, summarize(fieldErrors), req, fieldErrors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "Nedostaje parametar '" + ex.getParameterName() + "'.",
                req, Map.of(ex.getParameterName(), "is required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        Class<?> target = ex.getRequiredType();
        String msg = (target != null && target.isEnum())
                ? "'" + ex.getValue() + "' nije dozvoljena vrednost za '" + ex.getName() + "'. Moze biti: " + enumValues(target) + "."
                : "'" + ex.getValue() + "' nije validna vrednost za '" + ex.getName() + "'.";
        return respond(HttpStatus.BAD_REQUEST, msg, req, Map.of(ex.getName(), "has an invalid value"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "value" : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            Class<?> target = ife.getTargetType();
            String msg = (target != null && target.isEnum())
                    ? "'" + ife.getValue() + "' nije dozvoljena vrednost za " + field + ". Moze biti: " + enumValues(target) + "."
                    : "'" + ife.getValue() + "' nije validna vrednost za " + field + ".";
            return respond(HttpStatus.BAD_REQUEST, msg, req, Map.of(field, "has an invalid value"));
        }
        return respond(HttpStatus.BAD_REQUEST, "Telo zahteva nije validan JSON.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> fallback(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error: " + ex.getMessage(), req, null);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest req, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), fieldErrors));
    }

    private static String summarize(Map<String, String> fieldErrors) {
        if (fieldErrors.isEmpty()) return "The submitted data is not valid.";
        return fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private static String enumValues(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        return constants == null ? "" : Arrays.stream(constants).map(Object::toString).collect(Collectors.joining(", "));
    }
}
