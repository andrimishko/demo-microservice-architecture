package ua.lab3.common;

/** Publishing to the message broker failed (broker down or not answering). */
public class BrokerUnavailableException extends IllegalStateException {
    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
