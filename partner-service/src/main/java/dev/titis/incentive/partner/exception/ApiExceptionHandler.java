package dev.titis.incentive.partner.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException e, HttpServletRequest r) {
        return problem(e.getStatus(), e.getCode(), e.getMessage(), r, null);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        var fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage())).toList();
        return problem(400, "VALIDATION_ERROR", "Request validation failed", r, fields);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> malformed(Exception e, HttpServletRequest r) {
        return problem(400, "INVALID_REQUEST", "Invalid JSON, parameter, date, UUID or enum", r, null);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException e, HttpServletRequest r) {
        return problem(409, "DATA_CONFLICT", "Unique reference or data constraint conflict", r, null);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest r) {
        LOG.error("Unhandled request failure at {}", r.getRequestURI(), e);
        return problem(500, "INTERNAL_ERROR", "Unexpected internal error", r, null);
    }
    private ResponseEntity<ProblemDetail> problem(int status, String code, String detail,
                                                HttpServletRequest r, List<FieldError> fields) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        p.setType(URI.create("urn:incentive:problem:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        p.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        p.setInstance(URI.create(r.getRequestURI()));
        p.setProperty("code", code);
        if (fields != null) p.setProperty("fieldErrors", fields);
        return ResponseEntity.status(status).body(p);
    }
    public record FieldError(String field, String message) { }
}