package ua.lab3.mock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A parsed selector: metric name and optional instance label (null = all instances). */
public record PromQuery(String metric, String instance) {
    // shortcut: only `name` or `name{instance="x"}`; no other labels, regex matchers, functions or operators. Upgrade: a real PromQL parser.
    private static final Pattern SELECTOR = Pattern.compile(
            "\\s*([a-zA-Z_:][a-zA-Z0-9_:]*)\\s*(?:\\{\\s*(?:instance\\s*=\\s*\"([^\"]*)\")?\\s*})?\\s*");

    public static PromQuery parse(String query) {
        Matcher m = SELECTOR.matcher(query);
        if (!m.matches()) throw new IllegalArgumentException("unsupported query: " + query);
        return new PromQuery(m.group(1), m.group(2));
    }
}
