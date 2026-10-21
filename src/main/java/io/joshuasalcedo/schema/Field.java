package io.joshuasalcedo.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One field of a {@link Model}: its name, type and the rules its values follow.
 * <p>
 * Start a field with the factory for its type and chain the options:
 * <pre>{@code
 * Field.text("email").required().term("email-address").pattern(".+@.+")
 * Field.choice("status", "active", "closed")
 * Field.model("billingAddress", "address")
 * }</pre>
 * A field is immutable. Absent text parts are stored as empty strings and absent choices as an empty list, never
 * null. Whether a field is valid is decided by {@link SchemaValidator}, not when it is built, so a half-finished
 * design can still be built and inspected.
 *
 * @param name        as written in code, e.g. {@code email}: unique within the model (ignoring case), starts with a
 *                    letter, then letters, digits and underscores, at most 64 characters
 * @param type        the kind of value the field holds
 * @param term        optional glossary term that defines what the field means; empty when none
 * @param model       {@link FieldType#MODEL} only: the ID of the model this field holds; empty otherwise
 * @param choices     {@link FieldType#CHOICE} only: the allowed values, in display order
 * @param required    a value must be present (for a repeated field: at least one value)
 * @param repeated    the field holds a list of values rather than one
 * @param identity    entities only: this field is (part of) the identity, so it makes up the record ID and never
 *                    changes
 * @param pattern     optional regular expression a {@link FieldType#TEXT} value must match in full, after
 *                    {@code normalize}; empty when none
 * @param description extra notes beyond the term's definition; empty when none
 * @param normalize   TEXT and CHOICE only: clean-ups applied in order before the value is checked and stored, e.g.
 *                    trim then lower-case; empty for none
 * @param min         INTEGER and DECIMAL only: the smallest allowed value, inclusive; null for no lower limit
 * @param max         INTEGER and DECIMAL only: the largest allowed value, inclusive; null for no upper limit
 * @param minLength   TEXT only: the fewest characters allowed, after {@code normalize}; null for no limit
 * @param maxLength   TEXT only: the most characters allowed, after {@code normalize}; null for no limit
 * @param scale       DECIMAL only: values are rounded half-even to this many decimal places before they are checked
 *                    and stored (as {@code BigDecimal.setScale} does); null to keep them as given
 */
public record Field(String name, FieldType type, String term, String model, List<String> choices, boolean required,
                    boolean repeated, boolean identity, String pattern, String description,
                    List<Normalize> normalize, BigDecimal min, BigDecimal max, Integer minLength, Integer maxLength,
                    Integer scale) {

    /** Replaces null text with empty strings and copies the lists so the field cannot change later. */
    public Field {
        name = Objects.requireNonNullElse(name, "");
        term = Objects.requireNonNullElse(term, "");
        model = Objects.requireNonNullElse(model, "");
        choices = choices == null ? List.of() : List.copyOf(choices);
        pattern = Objects.requireNonNullElse(pattern, "");
        description = Objects.requireNonNullElse(description, "");
        normalize = normalize == null ? List.of() : List.copyOf(normalize);
    }

    /**
     * A field without clean-ups or limits: the form before {@code normalize}, {@code min}, {@code max},
     * {@code minLength}, {@code maxLength} and {@code scale} existed, kept so existing callers still compile.
     *
     * @param name        the field name
     * @param type        the field type
     * @param term        the glossary term, or empty
     * @param model       the held model's ID, for MODEL fields
     * @param choices     the allowed values, for CHOICE fields
     * @param required    a value must be present
     * @param repeated    a list of values
     * @param identity    part of an entity's identity
     * @param pattern     the full-match regular expression, or empty
     * @param description notes, or empty
     */
    public Field(String name, FieldType type, String term, String model, List<String> choices, boolean required,
                 boolean repeated, boolean identity, String pattern, String description) {
        this(name, type, term, model, choices, required, repeated, identity, pattern, description, List.of(), null,
             null, null, null, null);
    }

    /**
     * Starts a field of any type; the typed factories below are shorter for the common cases.
     *
     * @param name the field name
     * @param type the field type
     * @return a builder for the field
     */
    public static Builder of(String name, FieldType type) {
        return new Builder(name, type);
    }

    /**
     * Starts a {@link FieldType#TEXT} field.
     *
     * @param name the field name
     * @return a builder for the field
     */
    public static Builder text(String name) {
        return of(name, FieldType.TEXT);
    }

    /**
     * Starts an {@link FieldType#INTEGER} field (a whole number, stored as {@code Long}).
     *
     * @param name the field name
     * @return a builder for the field
     */
    public static Builder integer(String name) {
        return of(name, FieldType.INTEGER);
    }

    /**
     * Starts a {@link FieldType#DECIMAL} field (stored as {@code BigDecimal}, so money keeps its exact value).
     *
     * @param name the field name
     * @return a builder for the field
     */
    public static Builder decimal(String name) {
        return of(name, FieldType.DECIMAL);
    }

    /**
     * Starts a {@link FieldType#BOOLEAN} field.
     *
     * @param name the field name
     * @return a builder for the field
     */
    public static Builder bool(String name) {
        return of(name, FieldType.BOOLEAN);
    }

    /**
     * Starts a {@link FieldType#DATE} field (a calendar day, no time or zone).
     *
     * @param name the field name
     * @return a builder for the field
     */
    public static Builder date(String name) {
        return of(name, FieldType.DATE);
    }

    /**
     * Starts a {@link FieldType#TIMESTAMP} field (an instant in UTC).
     *
     * @param name the field name
     * @return a builder for the field
     */
    public static Builder timestamp(String name) {
        return of(name, FieldType.TIMESTAMP);
    }

    /**
     * Starts a {@link FieldType#CHOICE} field whose value must be one of {@code choices}.
     *
     * @param name    the field name
     * @param choices the allowed values; at least one must not be blank
     * @return a builder for the field
     */
    public static Builder choice(String name, String... choices) {
        return of(name, FieldType.CHOICE).choices(choices);
    }

    /**
     * Starts a {@link FieldType#MODEL} field holding another model. If that model is a value object its values are
     * embedded whole; if it is an entity the field holds the ID of one of its records.
     *
     * @param name    the field name
     * @param modelId the ID of the model the field holds
     * @return a builder for the field
     */
    public static Builder model(String name, String modelId) {
        return of(name, FieldType.MODEL).model(modelId);
    }

    /**
     * A builder holding this field's values, to make a changed copy.
     *
     * @return a builder starting from this field
     */
    public Builder toBuilder() {
        Builder b = new Builder(name, type);
        b.term = term;
        b.model = model;
        b.choices.addAll(choices);
        b.required = required;
        b.repeated = repeated;
        b.identity = identity;
        b.pattern = pattern;
        b.description = description;
        b.normalize.addAll(normalize);
        b.min = min;
        b.max = max;
        b.minLength = minLength;
        b.maxLength = maxLength;
        b.scale = scale;
        return b;
    }

    /**
     * Builds a {@link Field} step by step. Every option returns the builder so calls can be chained; nothing is
     * checked until the field is validated as part of a model.
     */
    public static final class Builder {
        private final String name;
        private final FieldType type;
        private String term = "";
        private String model = "";
        private final List<String> choices = new ArrayList<>();
        private boolean required;
        private boolean repeated;
        private boolean identity;
        private String pattern = "";
        private String description = "";
        private final List<Normalize> normalize = new ArrayList<>();
        private BigDecimal min;
        private BigDecimal max;
        private Integer minLength;
        private Integer maxLength;
        private Integer scale;

        private Builder(String name, FieldType type) {
            this.name = name;
            this.type = type;
        }

        /**
         * Points the field to the glossary term that defines it.
         *
         * @param term the term's name
         * @return this builder
         */
        public Builder term(String term) {
            this.term = term;
            return this;
        }

        /**
         * Sets the model a {@link FieldType#MODEL} field holds.
         *
         * @param modelId the model's ID
         * @return this builder
         */
        public Builder model(String modelId) {
            this.model = modelId;
            return this;
        }

        /**
         * Adds allowed values to a {@link FieldType#CHOICE} field.
         *
         * @param choices the values, appended in order
         * @return this builder
         */
        public Builder choices(String... choices) {
            this.choices.addAll(List.of(choices));
            return this;
        }

        /**
         * Makes a value mandatory.
         *
         * @return this builder
         */
        public Builder required() {
            return required(true);
        }

        /**
         * Sets whether a value is mandatory.
         *
         * @param required true if a value must be present
         * @return this builder
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Makes the field hold a list of values.
         *
         * @return this builder
         */
        public Builder repeated() {
            return repeated(true);
        }

        /**
         * Sets whether the field holds a list of values.
         *
         * @param repeated true for a list
         * @return this builder
         */
        public Builder repeated(boolean repeated) {
            this.repeated = repeated;
            return this;
        }

        /**
         * Makes the field (part of) the identity of an entity.
         *
         * @return this builder
         */
        public Builder identity() {
            return identity(true);
        }

        /**
         * Sets whether the field is (part of) the identity of an entity.
         *
         * @param identity true if it identifies the record
         * @return this builder
         */
        public Builder identity(boolean identity) {
            this.identity = identity;
            return this;
        }

        /**
         * Sets the regular expression a {@link FieldType#TEXT} value must match in full.
         *
         * @param pattern a {@link java.util.regex.Pattern} expression
         * @return this builder
         */
        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        /**
         * Sets notes beyond the term's definition.
         *
         * @param description free text
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds clean-ups applied, in order, before a TEXT or CHOICE value is checked and stored.
         *
         * @param steps the clean-ups, appended after any already added
         * @return this builder
         */
        public Builder normalize(Normalize... steps) {
            normalize.addAll(List.of(steps));
            return this;
        }

        /**
         * Sets the smallest allowed value of an INTEGER or DECIMAL field, inclusive.
         *
         * @param min the lower limit, given as text so decimals stay exact, e.g. {@code "-90"} or {@code "0.01"}
         * @return this builder
         */
        public Builder min(String min) {
            this.min = new BigDecimal(min);
            return this;
        }

        /**
         * Sets the largest allowed value of an INTEGER or DECIMAL field, inclusive.
         *
         * @param max the upper limit, given as text so decimals stay exact, e.g. {@code "65535"}
         * @return this builder
         */
        public Builder max(String max) {
            this.max = new BigDecimal(max);
            return this;
        }

        /**
         * Sets both limits of an INTEGER or DECIMAL field, inclusive.
         *
         * @param min the lower limit, as text
         * @param max the upper limit, as text
         * @return this builder
         */
        public Builder range(String min, String max) {
            return min(min).max(max);
        }

        /**
         * Sets the fewest characters a TEXT value may have.
         *
         * @param minLength 0 or more
         * @return this builder
         */
        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        /**
         * Sets the most characters a TEXT value may have.
         *
         * @param maxLength 0 or more
         * @return this builder
         */
        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /**
         * Rounds DECIMAL values half-even to this many decimal places before they are checked and stored.
         *
         * @param scale 0 to 18
         * @return this builder
         */
        public Builder scale(int scale) {
            this.scale = scale;
            return this;
        }

        /**
         * Makes the field.
         *
         * @return an immutable field with the options set so far
         */
        public Field build() {
            return new Field(name, type, term, model, choices, required, repeated, identity, pattern, description,
                             normalize, min, max, minLength, maxLength, scale);
        }
    }
}
