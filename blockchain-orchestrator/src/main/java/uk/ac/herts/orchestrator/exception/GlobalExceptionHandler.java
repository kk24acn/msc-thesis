package uk.ac.herts.orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.ac.herts.orchestrator.api.dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TransactionSubmissionException.class)
    public ResponseEntity<ApiErrorResponse> handleSubmissionFailure(TransactionSubmissionException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("TRANSACTION_SUBMISSION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(TransactionSigningException.class)
    public ResponseEntity<ApiErrorResponse> handleSigningFailure(TransactionSigningException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("TRANSACTION_SIGNING_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(TransactionConfirmationException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TransactionConfirmationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("TRANSACTION_CONFIRMATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("INVALID_STATE", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
