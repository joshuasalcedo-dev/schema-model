package io.joshuasalcedo.schema;

/**
 * One reason a model, record or action is refused. Problems are collected rather than thrown one at a time, so a
 * caller can show every mistake at once.
 *
 * @param path    where the problem is, e.g. {@code models[customer].fields[2].pattern} or {@code record.email}; a UI
 *                can use it to mark the right input
 * @param message what is wrong, as a full sentence starting with the path, ready to show to a person
 */
public record SchemaProblem(String path, String message) {

    /**
     * The message alone.
     *
     * @return {@link #message()}
     */
    @Override
    public String toString() {
        return message;
    }
}
