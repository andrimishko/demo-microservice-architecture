package ua.lab3.aggregator;

/** Domain view of one log line (OpenSearch JSON stays inside OpenSearchClient). */
public record LogRecord(String severity, String message) {}
