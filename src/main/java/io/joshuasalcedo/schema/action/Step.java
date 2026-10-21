package io.joshuasalcedo.schema.action;

/**
 * One thing an {@link Action} does. Steps run in order after all rules passed; a later step sees the changes of
 * earlier ones. If any step fails, nothing is saved.
 */
public sealed interface Step {

    /**
     * Sets a field to the value of a CEL expression; a {@code null} result clears the field.
     *
     * @param field      the name of a field of the action's model; never an identity field of an existing record
     * @param expression CEL, e.g. {@code input.reason}, {@code 'closed'} or {@code record.points + 10}
     */
    record Set(String field, String expression) implements Step {
    }

    /**
     * Calls Java code registered on the engine, for logic too big for an expression.
     *
     * @param handler the name the handler was registered under, e.g. {@code billing.refund}
     */
    record Call(String handler) implements Step {
    }

    /**
     * Publishes an event once the action has saved; nothing is published if it fails.
     *
     * @param event the event name, e.g. {@code customer.closed}: letters, digits, {@code _ . -}
     */
    record Emit(String event) implements Step {
    }
}
