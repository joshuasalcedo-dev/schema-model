package io.joshuasalcedo.schema.port.in;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.action.Action;
import io.joshuasalcedo.schema.record.Record;

/**
 * Inbound port: the one entry point of the platform. Models are designed through {@link #models()}, logic is defined
 * as {@link Action}s, and data lives in {@link Record}s that always fit their model. A REST controller, gRPC service,
 * desktop app or CLI calls this and nothing else. Implemented by {@link io.joshuasalcedo.schema.engine.Engine}.
 * <p>
 * Refusals are {@link io.joshuasalcedo.schema.SchemaException}s whose {@code reason()} says why and whose {@code problems()}
 * list every mistake.
 */
public interface Platform {

    /**
     * Create, change and check models. Besides the model rules, deleting a model is refused while records or
     * actions use it, and an entity with records cannot become a value object.
     *
     * @return the models
     */
    ModelDesign models();

    // --- actions -------------------------------------------------------------------------------------------

    /**
     * Adds an action, or replaces the one with the same name. It is live immediately.
     *
     * @param action the action
     * @return the saved action
     * @throws io.joshuasalcedo.schema.SchemaException INVALID with every problem if it breaks a rule (see
     *                                       {@link io.joshuasalcedo.schema.action.ActionValidator})
     */
    Action defineAction(Action action);

    /**
     * Removes an action.
     *
     * @param name the action's name
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND if there is no such action
     */
    void removeAction(String name);

    /**
     * Looks up an action.
     *
     * @param name the action's name
     * @return the action, or empty if there is none
     */
    Optional<Action> action(String name);

    /**
     * All actions.
     *
     * @return the actions in the repository's order
     */
    List<Action> actions();

    /**
     * Runs an action: converts the input, checks the rules, runs the steps, checks the record against its model,
     * saves it and publishes the events. All or nothing.
     *
     * @param action   the action's name
     * @param recordId the ID of the record to run on; null for an action that creates one
     * @param input    the input values by field name; null for none
     * @return the record as saved
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND for an unknown action or record; INVALID for bad input or a
     *                                       result that does not fit the model; RULE_FAILED with the rule's message;
     *                                       ALREADY_EXISTS when a creating action makes an ID that is taken;
     *                                       CONFLICT if the record changed while the action ran
     */
    Record run(String action, String recordId, Map<String, ?> input);

    // --- files ---------------------------------------------------------------------------------------------

    /**
     * Stores a file's bytes, to then put the result in a record (in a {@code file}, {@code image}, {@code video} or
     * {@code audio} field). The same bytes uploaded twice are stored once.
     *
     * @param name        the file name, e.g. {@code contract.pdf}
     * @param contentType the media type; blank to guess from the name
     * @param content     the bytes; read to the end, not closed
     * @return the stored file, whose {@link io.joshuasalcedo.schema.file.FileRef#toValues()} go into a record
     * @throws io.joshuasalcedo.schema.SchemaException INVALID when the file is larger than the engine allows
     */
    io.joshuasalcedo.schema.file.FileRef upload(String name, String contentType, java.io.InputStream content);

    /**
     * Opens a stored file's bytes.
     *
     * @param location a {@link io.joshuasalcedo.schema.file.FileRef#location()}
     * @return the bytes, for the caller to close; empty when nothing is stored there
     */
    Optional<java.io.InputStream> download(String location);

    // --- records -------------------------------------------------------------------------------------------

    /**
     * Stores a new record of an entity model. Its ID comes from its identity fields, or is a random UUID for an
     * entity without any.
     *
     * @param model  the entity model's ID
     * @param values the values by field name, in any type {@link io.joshuasalcedo.schema.record.RecordValues} accepts
     * @return the saved record, version 1
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND for an unknown model; INVALID if the values do not fit, a
     *                                       referenced record does not exist, or the model is a value object;
     *                                       ALREADY_EXISTS if a record with that ID exists
     */
    Record create(String model, Map<String, ?> values);

    /**
     * Changes some fields of a record; fields not in {@code changes} keep their values.
     *
     * @param model           the entity model's ID
     * @param id              the record's ID
     * @param changes         new values by field name; a null value clears the field (use a map that allows nulls)
     * @param expectedVersion the version the caller read, to refuse the change if someone else saved since; 0 to
     *                        skip that check
     * @return the saved record, one version higher
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND for an unknown model or record; INVALID if the result does not
     *                                       fit or an identity field changes; CONFLICT on a version mismatch
     */
    Record update(String model, String id, Map<String, ?> changes, long expectedVersion);

    /**
     * Removes a record.
     *
     * @param model the entity model's ID
     * @param id    the record's ID
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND if there is no such record; IN_USE while another record refers
     *                                       to it; CONFLICT if it changed during the call
     */
    void delete(String model, String id);

    /**
     * Looks up a record.
     *
     * @param model the entity model's ID
     * @param id    the record's ID
     * @return the record, or empty if there is none
     */
    Optional<Record> get(String model, String id);

    /**
     * All records of a model.
     *
     * @param model the entity model's ID
     * @return the records in the repository's order; empty for an unknown model
     */
    List<Record> list(String model);
}
