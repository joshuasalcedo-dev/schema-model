package io.joshuasalcedo.schema.rules;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;

/**
 * One blank of a {@link RuleTemplate} that the user fills in: a field of the model, several fields, or a value typed
 * in. Arguments are given as text by {@link #name()} (see {@link RuleTemplate#create}).
 *
 * @param name   the key of the argument, e.g. {@code field}
 * @param label  what a form shows next to the input, e.g. "Not before"
 * @param kind   what the argument is: one field, several fields or a value
 * @param types  FIELD and FIELDS only: the field types allowed, in which case repeated fields are not allowed either;
 *               empty for any field at all (also repeated and MODEL fields); always empty for VALUE
 * @param typeOf the name of another (FIELD) parameter this one is tied to: a FIELD must have the same type as that
 *               parameter's field, a VALUE is written in that field's type (e.g. one of its choices); empty when free
 */
public record RuleParameter(String name, String label, Kind kind, Set<FieldType> types, String typeOf) {

    /** What kind of argument a parameter takes. */
    public enum Kind {
        /** The name of one field of the model. */
        FIELD,
        /** The names of two or more different fields of the model, separated by commas, e.g. {@code "a, b"}. */
        FIELDS,
        /** A literal the user types, e.g. a choice value or a number. */
        VALUE
    }

    /** Copies the types so they cannot change and replaces null text with empty strings. */
    public RuleParameter {
        name = Objects.requireNonNullElse(name, "");
        label = Objects.requireNonNullElse(label, "");
        Objects.requireNonNull(kind, "kind");
        types = types == null || types.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(types));
        typeOf = Objects.requireNonNullElse(typeOf, "");
    }

    /**
     * Whether a field may fill this parameter, on its own (a {@link #typeOf()} tie is checked by the template).
     *
     * @param field a field of the model
     * @return false for VALUE parameters; for FIELD and FIELDS true when {@link #types()} is empty or the field is not
     *         repeated and has one of the types
     */
    public boolean accepts(Field field) {
        if (kind == Kind.VALUE) {
            return false;
        }
        return types.isEmpty() || (!field.repeated() && types.contains(field.type()));
    }
}
