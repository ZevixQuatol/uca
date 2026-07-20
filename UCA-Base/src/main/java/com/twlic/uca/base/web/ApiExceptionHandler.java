package com.twlic.uca.base.web;

import com.twlic.uca.base.registry.RegistryError;
import com.twlic.uca.base.registry.RegistryException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RegistryException.class)
    public ResponseEntity<ProblemDetail> handleRegistryException(RegistryException exception) {
        HttpStatus status = switch (exception.error()) {
            case APPLICATION_NOT_FOUND, INSTANCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NO_ONLINE_INSTANCE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail problem = problem(status, exception.error().name(), exception.getMessage());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            RequestValidationException.class
    })
    public ResponseEntity<ProblemDetail> handleValidationException(Exception exception) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                validationDetail(exception));
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }

    private static String validationDetail(Exception exception) {
        if (exception instanceof RequestValidationException) {
            return exception.getMessage();
        }
        return "Request validation failed";
    }
}
