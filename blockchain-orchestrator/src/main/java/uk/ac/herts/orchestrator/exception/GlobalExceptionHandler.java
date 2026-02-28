package uk.ac.herts.orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.ac.herts.orchestrator.api.dto.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionSubmissionException.class)
    public ResponseEntity<ApiErrorResponse> handleSubmissionFailure(TransactionSubmissionException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("TRANSACTION_SUBMISSION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TransactionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("TRANSACTION_NOT_FOUND", exception.getMessage()));
    }
}
