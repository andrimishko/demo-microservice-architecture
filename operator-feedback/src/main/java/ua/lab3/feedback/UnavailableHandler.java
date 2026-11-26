package ua.lab3.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ua.lab3.common.BrokerUnavailableException;

/** Database or broker down: a clear 503 problem+json instead of a bare 500. */
@RestControllerAdvice
class UnavailableHandler {
    private static final Logger log = LoggerFactory.getLogger(UnavailableHandler.class);

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class, BrokerUnavailableException.class})
    ProblemDetail unavailable(Exception e) {
        log.warn("dependency unavailable: {}", e.toString());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "a dependency (database or message broker) is unavailable, try again later");
    }
}
