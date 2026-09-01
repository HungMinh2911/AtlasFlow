package com.atlasflow.shared.error;

import com.atlasflow.file.domain.FileNotFoundException;
import com.atlasflow.job.domain.JobValidationException;
import com.atlasflow.job.domain.IdempotencyKeyReusedException;
import com.atlasflow.job.domain.JobNotFoundException;
import com.atlasflow.job.execution.domain.JobExecutionNotFoundException;
import com.atlasflow.file.domain.FileUploadException;
import com.atlasflow.file.domain.InvalidFileException;
import com.atlasflow.project.domain.ProjectNameAlreadyExistsException;
import com.atlasflow.project.domain.ProjectNotExistsException;
import com.atlasflow.workflow.domain.WorkflowNotFoundException;
import com.atlasflow.workflow.step.domain.InvalidWorkflowStepConfigException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingRequestHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getHeaderName() + " header is required"
        );
        problemDetail.setTitle("Missing request header");
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty("request_id", request.getHeader("X-Request-Id"));
        return problemDetail;
    }

    @ExceptionHandler(ProjectNameAlreadyExistsException.class)
    public ProblemDetail handProjectNameExists(
            ProjectNameAlreadyExistsException existsException,
            HttpServletRequest request
    ){
        ProblemDetail problemDetail  = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                existsException.getMessage()
        );

        problemDetail.setTitle("Project name exists");
        problemDetail.setProperty("code","PROJECT_NAME_ALREADY_EXISTS");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );
        return problemDetail;
    }

    @ExceptionHandler(ProjectNotExistsException.class)
    public ProblemDetail handleProjectNotExists(
            ProjectNotExistsException existsException,
            HttpServletRequest request

    ){
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                existsException.getMessage()
        );

        problemDetail.setTitle("Project not exists with this ID");
        problemDetail.setProperty("code","PROJECT_NOT_EXISTS");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );
        return problemDetail;

    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    public ProblemDetail handleWorkflowNotFound(
            WorkflowNotFoundException notFoundException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                notFoundException.getMessage()
        );

        problemDetail.setTitle("Workflow not found");
        problemDetail.setProperty("code", "WORKFLOW_NOT_FOUND");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(InvalidWorkflowStepConfigException.class)
    public ProblemDetail handleInvalidWorkflowStepConfig(
            InvalidWorkflowStepConfigException invalidException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                invalidException.getMessage()
        );

        problemDetail.setTitle("Invalid workflow step configuration");
        problemDetail.setProperty("code", "INVALID_WORKFLOW_STEP_CONFIG");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ProblemDetail handleFileNotFound(
            FileNotFoundException fileNotFoundException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                fileNotFoundException.getMessage()
        );

        problemDetail.setTitle("File not found");
        problemDetail.setProperty("code", "FILE_NOT_FOUND");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalidFile(
            InvalidFileException invalidException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                invalidException.getMessage()
        );

        problemDetail.setTitle("Invalid file");
        problemDetail.setProperty("code", "INVALID_FILE");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(FileUploadException.class)
    public ProblemDetail handleFileUpload(
            FileUploadException uploadException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                uploadException.getMessage()
        );

        problemDetail.setTitle("File upload failed");
        problemDetail.setProperty("code", "FILE_UPLOAD_FAILED");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(JobValidationException.class)
    public ProblemDetail handleJobValidation(
            JobValidationException validationException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                validationException.getMessage()
        );

        problemDetail.setTitle("Invalid job request");
        problemDetail.setProperty("code", "INVALID_JOB_REQUEST");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleIdempotencyKeyReused(
            IdempotencyKeyReusedException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The Idempotency-Key was already used with a different request"
        );
        problemDetail.setTitle("Idempotency key reused");
        problemDetail.setProperty("code", "IDEMPOTENCY_KEY_REUSED");
        problemDetail.setProperty("request_id", request.getHeader("X-Request-Id"));
        return problemDetail;
    }

    @ExceptionHandler(JobExecutionNotFoundException.class)
    public ProblemDetail handleJobExecutionNotFound(
            JobExecutionNotFoundException notFoundException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                notFoundException.getMessage()
        );

        problemDetail.setTitle("Job execution not found");
        problemDetail.setProperty("code", "JOB_EXECUTION_NOT_FOUND");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ProblemDetail handleJobNotFound(
            JobNotFoundException notFoundException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                notFoundException.getMessage()
        );

        problemDetail.setTitle("Job not found");
        problemDetail.setProperty("code", "JOB_NOT_FOUND");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(
            MethodArgumentNotValidException validationException,
            HttpServletRequest request
    ) {
        String detail = validationException.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                detail
        );

        problemDetail.setTitle("Validation failed");
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(
            HttpMessageNotReadableException notReadableException,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an invalid value"
        );

        problemDetail.setTitle("Malformed request");
        problemDetail.setProperty("code", "MALFORMED_REQUEST");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleInvalidState(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problemDetail.setTitle("Invalid state transition");
        problemDetail.setProperty("code", "INVALID_STATE_TRANSITION");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage() != null ? exception.getMessage() : "Access denied"
        );
        problemDetail.setTitle("Access denied");
        problemDetail.setProperty("code", "ACCESS_DENIED");
        problemDetail.setProperty("request_id", request.getHeader("X-Request-Id"));
        return problemDetail;
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(
            SecurityException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage()
        );
        problemDetail.setTitle("Security violation");
        problemDetail.setProperty("code", "TOKEN_REPLAY_DETECTED");
        problemDetail.setProperty("request_id", request.getHeader("X-Request-Id"));
        return problemDetail;
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(
            org.springframework.security.core.AuthenticationException exception,
            HttpServletRequest request
    ) {
        String errorCode = "AUTH_FAILED";
        if (exception instanceof org.springframework.security.authentication.BadCredentialsException) {
            errorCode = "AUTH_INVALID_CREDENTIALS";
        } else if (exception instanceof org.springframework.security.authentication.LockedException) {
            errorCode = "AUTH_ACCOUNT_LOCKED";
        } else if (exception instanceof org.springframework.security.authentication.DisabledException) {
            errorCode = "AUTH_ACCOUNT_DISABLED";
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage()
        );
        problemDetail.setTitle("Authentication failed");
        problemDetail.setProperty("code", errorCode);
        problemDetail.setProperty("request_id", request.getHeader("X-Request-Id"));
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );

        problemDetail.setTitle("Internal server error");
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        problemDetail.setProperty(
                "request_id",
                request.getHeader("X-Request-Id")
        );

        return problemDetail;
    }
}
