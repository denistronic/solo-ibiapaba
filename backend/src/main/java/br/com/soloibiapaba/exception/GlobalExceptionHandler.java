package br.com.soloibiapaba.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SampleNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSampleNotFound(
            SampleNotFoundException exception,
            HttpServletRequest request) {

        return build(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisNotFound(
            AnalysisNotFoundException exception,
            HttpServletRequest request) {

        return build(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException exception,
            HttpServletRequest request) {

        return build(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler({LastAdminException.class, DuplicateEmailException.class})
    public ResponseEntity<ApiErrorResponse> handleUserConflict(
            RuntimeException exception,
            HttpServletRequest request) {

        return build(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            org.springframework.security.core.AuthenticationException exception,
            HttpServletRequest request) {

        return build(
                HttpStatus.UNAUTHORIZED,
                "Credenciais inválidas",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return build(
                HttpStatus.BAD_REQUEST,
                "Dados inválidos",
                request.getRequestURI(),
                fields
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInput(IllegalArgumentException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Os dados entram em conflito com um registro existente ou com as regras do banco.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Corpo JSON inválido. Confira os tipos, números e valores dos campos.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidArgument(HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro ausente ou inválido. Confira os identificadores e os campos obrigatórios.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleDenied(org.springframework.security.access.AccessDeniedException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN,e.getMessage(),request.getRequestURI(),Map.of());
    }
    @ExceptionHandler({SampleConflictException.class, org.springframework.dao.ConcurrencyFailureException.class})
    public ResponseEntity<ApiErrorResponse> handleRecordConflict(RuntimeException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,"O registro foi alterado ou está em uso. Reabra a amostra e confira os dados antes de salvar.",request.getRequestURI(),Map.of());
    }
    @ExceptionHandler(MissingRecordVersionException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingVersion(MissingRecordVersionException e, HttpServletRequest request) {
        return build(HttpStatus.PRECONDITION_REQUIRED,e.getMessage(),request.getRequestURI(),Map.of());
    }
    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> fields) {

        var body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fields
        );

        return ResponseEntity.status(status).body(body);
    }
}
