package io.joshuasalcedo.schema;

import java.util.Objects;

/**
 * A rule across the fields of one model that every value must keep, such as "the end is not before the start".
 * It is a CEL expression checked whenever a value of the model is stored, also when the value is embedded in
 * another model; a value that breaks it is refused with {@link #message()}.
 * <p>
 * The expression sees {@code value} (the object's values by field name, in the types CEL uses: DECIMAL as a double,
 * DATE and TIMESTAMP as ISO-8601 strings that compare correctly as text), {@code today} (the current date) and
 * {@code now} (the current instant). An absent field has no key: guard optional fields with
 * {@code has(value.field)}.
 * <pre>{@code
 * Model.valueObject("DateRange")
 *         .field(Field.date("startDate").required())
 *         .field(Field.date("endDate").required())
 *         .invariant("value.endDate >= value.startDate", "the end date is before the start date")
 * }</pre>
 *
 * @param expression CEL that gives true or false
 * @param message    what the caller is told when it is false; defaults to {@code "requires <expression>"}
 */
public record Invariant(String expression, String message) {

    /** Replaces a null expression with an empty one and a missing message with the default. */
    public Invariant {
        expression = Objects.requireNonNullElse(expression, "");
        message = message == null || message.isBlank() ? "requires " + expression : message;
    }
}
