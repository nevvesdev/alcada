package br.com.nevvesdev.alcada.api;

import br.com.nevvesdev.alcada.application.exception.CreditRequestNotFoundException;
import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyReuseException;
import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import br.com.nevvesdev.alcada.domain.exception.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(CreditRequestNotFoundException.class)
    public ProblemDetail handleNotFound(CreditRequestNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Solicitação não encontrada",
                exception.getMessage(), "CREDIT_REQUEST_NOT_FOUND");
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ProblemDetail handleIdempotencyKeyReuse(IdempotencyKeyReuseException exception) {
        return problem(UNPROCESSABLE, "Chave de idempotência reutilizada",
                exception.getMessage(), "IDEMPOTENCY_KEY_REUSED");
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyKeyConflict(IdempotencyKeyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflito de idempotência",
                exception.getMessage(), "IDEMPOTENCY_KEY_CONFLICT");
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ProblemDetail handleInvalidStateTransition(InvalidStateTransitionException exception) {
        return problem(HttpStatus.CONFLICT, "Estado da solicitação não permite a operação",
                exception.getMessage(), "INVALID_STATE");
    }

    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail handleDomainValidation(DomainValidationException exception) {
        return problem(UNPROCESSABLE, "Valor inválido", exception.getMessage(), "INVALID_VALUE");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Erro inesperado ao processar a requisição", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Não foi possível processar a requisição", "INTERNAL_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(status, "Requisição inválida", "Dados da requisição inválidos", "VALIDATION_ERROR");
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", violations);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}