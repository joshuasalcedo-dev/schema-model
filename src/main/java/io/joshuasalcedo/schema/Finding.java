package io.joshuasalcedo.schema;

/**
 * A quality issue in a valid schema: nothing is broken, but a model could be better described. Found by
 * {@link SchemaAnalysis}; unlike a {@link SchemaProblem} it never stops a change.
 *
 * @param check       which check found it
 * @param model       the model's ID
 * @param displayName the model's display name, for showing to a person
 * @param message     what to look at, as a short sentence
 */
public record Finding(Check check, String model, String displayName, String message) {

    /** The quality checks. */
    public enum Check {
        /** An entity with no identity field: nothing says which record is which, so records get random IDs. */
        ENTITY_WITHOUT_IDENTITY,
        /** No glossary term and no related articles explain the model. */
        MODEL_UNDOCUMENTED
    }
}
