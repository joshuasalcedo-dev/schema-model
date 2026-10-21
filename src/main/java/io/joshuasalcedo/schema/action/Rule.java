package io.joshuasalcedo.schema.action;

import java.util.Objects;

/**
 * A condition an {@link Action} needs before any step runs: a CEL expression that must be true.
 *
 * @param expression CEL that gives true or false, e.g. {@code record.status == 'active'}
 * @param message    what the caller is told when it is false; defaults to {@code "requires <expression>"}
 */
public record Rule(String expression, String message) {

    /** Replaces a null expression with an empty one and a missing message with the default. */
    public Rule {
        expression = Objects.requireNonNullElse(expression, "");
        message = message == null || message.isBlank() ? "requires " + expression : message;
    }
}
