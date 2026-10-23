package ru.strata.tasks.web;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.strata.tasks.service.InvalidTaskStateException;
import ru.strata.tasks.service.TaskNotFoundException;

/**
 * Единый формат ошибок — RFC 9457 (application/problem+json).
 * Advice привязан к контроллерам API, чтобы не перехватывать ошибки Actuator.
 */
@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TaskApiExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail handleNotFound(TaskNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Task not found");
        problem.setProperty("taskId", ex.taskId());
        return problem;
    }

    @ExceptionHandler(InvalidTaskStateException.class)
    ProblemDetail handleIllegalTransition(InvalidTaskStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Illegal status transition");
        problem.setProperty("taskId", ex.taskId());
        problem.setProperty("from", ex.from());
        problem.setProperty("to", ex.to());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid request body");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())
                ))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleMalformedRequest(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request");
        problem.setTitle("Invalid request");
        log.debug("Rejected request payload", ex);
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Task was changed by another request, retry with fresh data");
        problem.setTitle("Concurrent update");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        // Системные HTTP-ошибки Spring (405, 415 и т.п) отдаём как есть, не превращая в 500.
        if (ex instanceof ErrorResponseException errorResponse) {
            return errorResponse.getBody();
        }
        log.error("Unhandled exception while serving API request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error, contact service owner");
        problem.setTitle("Internal server error");
        return problem;
    }
}
