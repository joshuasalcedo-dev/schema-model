package io.joshuasalcedo.schema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown whenever the platform refuses a call: an invalid model, record or action, something missing, a conflict.
 * It carries every problem found, not just the first, and a {@link Reason} a service maps to its own error codes,
 * for example:
 * <table>
 *   <caption>Suggested mappings</caption>
 *   <tr><th>Reason</th><th>gRPC</th><th>HTTP</th></tr>
 *   <tr><td>INVALID</td><td>INVALID_ARGUMENT</td><td>400</td></tr>
 *   <tr><td>NOT_FOUND</td><td>NOT_FOUND</td><td>404</td></tr>
 *   <tr><td>ALREADY_EXISTS</td><td>ALREADY_EXISTS</td><td>409</td></tr>
 *   <tr><td>IN_USE</td><td>FAILED_PRECONDITION</td><td>409</td></tr>
 *   <tr><td>RULE_FAILED</td><td>FAILED_PRECONDITION</td><td>422</td></tr>
 *   <tr><td>CONFLICT</td><td>ABORTED</td><td>409</td></tr>
 * </table>
 */
public class SchemaException extends RuntimeException {

    /** Why a call was refused. */
    public enum Reason {
        /** The input breaks a rule; {@link #problems()} lists each one. */
        INVALID,
        /** A model, action or record that was asked for does not exist. */
        NOT_FOUND,
        /** Something with the same ID already exists. */
        ALREADY_EXISTS,
        /** It cannot be removed or changed that way while something else uses it. */
        IN_USE,
        /** An action's rule was false for this record and input; the message is the rule's message. */
        RULE_FAILED,
        /** The record changed since it was read (version mismatch): read it again and retry. */
        CONFLICT
    }

    /** Why the call was refused. */
    private final Reason reason;
    /** Every problem found, in the order found; never empty. */
    private final List<SchemaProblem> problems;

    /**
     * A refusal with several problems.
     *
     * @param reason   why it was refused
     * @param problems at least one problem; the exception's message joins their messages with {@code "; "}
     */
    public SchemaException(Reason reason, List<SchemaProblem> problems) {
        super(problems.stream().map(SchemaProblem::message).collect(Collectors.joining("; ")));
        this.reason = reason;
        this.problems = List.copyOf(problems);
    }

    /**
     * An {@link Reason#INVALID} refusal with several problems.
     *
     * @param problems at least one problem
     */
    public SchemaException(List<SchemaProblem> problems) {
        this(Reason.INVALID, problems);
    }

    /**
     * A refusal with one problem.
     *
     * @param reason  why it was refused
     * @param path    where the problem is
     * @param message what is wrong
     */
    public SchemaException(Reason reason, String path, String message) {
        this(reason, List.of(new SchemaProblem(path, message)));
    }

    /**
     * The {@link Reason#NOT_FOUND} refusal for a missing model.
     *
     * @param id the model ID that was asked for
     * @return the exception, for the caller to throw
     */
    public static SchemaException notFound(String id) {
        return new SchemaException(Reason.NOT_FOUND, id, "model '" + id + "' was not found");
    }

    /**
     * Why the call was refused.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Every problem found.
     *
     * @return at least one problem, in the order found
     */
    public List<SchemaProblem> problems() {
        return problems;
    }
}
