package zur.koeln.kickertool.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import zur.koeln.kickertool.application.ForbiddenException;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.NotPermittedException;
import zur.koeln.kickertool.domain.RuleViolationException;

/** Übersetzt Fachfehler in einheitliche Fehlerantworten (RFC 9457, {@code application/problem+json}). */
@RestControllerAdvice
class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, NotPermittedException.class})
    ProblemDetail forbidden(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Aktion passt nicht zum aktuellen Stand, z. B. falscher Turnierstatus oder Runde noch nicht abgeschlossen. */
    @ExceptionHandler(RuleViolationException.class)
    ProblemDetail conflict(RuleViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Ungültige Eingabe, z. B. ungültige UUID oder ein Ergebnis über dem Tor-Limit. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
