package ua.lab3.assessment;

/** Port: delivers an alert to the on-call tooling. Throws if it could not be delivered. */
public interface AlertSender {
    void send(Alert alert);
}
