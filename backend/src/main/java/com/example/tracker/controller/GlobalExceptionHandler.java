package com.example.tracker.controller;

import com.example.tracker.exception.ForbiddenException;
import com.example.tracker.exception.NotFoundException;
import com.example.tracker.exception.RateLimitedException;
import com.example.tracker.exception.TaskConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** All errors are RFC 9457 problem details: {@code {type,title,status,detail,...}}. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthorized(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(RateLimitedException.class)
    public ProblemDetail rateLimited(RateLimitedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /** Stale write: 409 with the current server copy, so the client can show a merge/retry UI. */
    @ExceptionHandler(TaskConflictException.class)
    public ProblemDetail conflict(TaskConflictException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, ex.getMessage());
        pd.setProperty("current", ex.getCurrent());
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail optimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "The resource was modified concurrently; reload and retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.debug("Integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ProblemDetail badRequest(Exception ex) {
        String detail = ex instanceof IllegalArgumentException ? ex.getMessage() : "Malformed request";
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
