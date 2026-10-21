package io.joshuasalcedo.schema;

/**
 * The type of a {@link Field}. Each type has one Java type in a stored record (see
 * {@link io.joshuasalcedo.schema.record.Record}).
 */
public enum FieldType {
    /** Free text, optionally matching the field's {@code pattern}; stored as {@code String}. */
    TEXT,
    /** A whole number; stored as {@code Long}. */
    INTEGER,
    /** An exact number such as an amount of money; stored as {@code BigDecimal}. */
    DECIMAL,
    /** True or false; stored as {@code Boolean}. */
    BOOLEAN,
    /** A calendar day; stored as {@code LocalDate}. */
    DATE,
    /** An instant in time; stored as {@code Instant}. */
    TIMESTAMP,
    /** One of the field's {@code choices}; stored as {@code String}. */
    CHOICE,
    /**
     * Another model (the field's {@code model}): a value object is embedded whole (stored as a {@code Map}), an
     * entity is referenced by its record ID (stored as {@code String}).
     */
    MODEL
}
