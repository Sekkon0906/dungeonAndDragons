package dev.mesarol.api;

import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException error) { return problem(HttpStatus.BAD_REQUEST, error.getMessage()); }
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException error) { return problem(HttpStatus.CONFLICT, error.getMessage()); }
    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing(NoSuchElementException error) { return problem(HttpStatus.NOT_FOUND, error.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity() { return problem(HttpStatus.CONFLICT, "El registro entra en conflicto con datos existentes. Vuelve a intentarlo."); }
    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
