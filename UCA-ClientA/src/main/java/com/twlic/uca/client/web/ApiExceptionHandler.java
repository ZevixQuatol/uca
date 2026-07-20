package com.twlic.uca.client.web;

import com.twlic.uca.client.demo.DemoCallException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DemoCallException.class)
    public ResponseEntity<ProblemDetail> handleDemoCallException(DemoCallException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(exception.status().getReasonPhrase());
        problem.setProperty("code", exception.code());
        return ResponseEntity.status(exception.status()).body(problem);
    }
}
