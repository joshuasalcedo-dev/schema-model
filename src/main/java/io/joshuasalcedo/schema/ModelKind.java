package io.joshuasalcedo.schema;

/** What kind of thing a {@link Model} describes; decides how other models hold it and whether it has records. */
public enum ModelKind {
    /**
     * Has an identity that stays the same while its values change (Customer, Order). It is stored as records, and
     * other models refer to it by the record's ID.
     */
    ENTITY,
    /**
     * Defined only by its values, no identity, replaced rather than changed (Money, EmailAddress, Address). It is
     * never stored on its own: other models embed it whole.
     */
    VALUE_OBJECT
}
