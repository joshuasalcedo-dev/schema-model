package io.joshuasalcedo.schema.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.action.Action;
import io.joshuasalcedo.schema.port.out.RecordRepository;
import io.joshuasalcedo.schema.record.Record;

/**
 * What an {@link ActionHandler} sees while an action runs. Changes to {@link #values()} are checked against the
 * model and saved together with the rest of the action; nothing is saved if the action fails. A context is only
 * valid during the handler call.
 */
public final class ActionContext {

    private final Action action;
    private final Record before;
    private final Map<String, Object> values;
    private final Map<String, Object> input;
    private final Instant now;
    private final List<String> events;
    private final RecordRepository records;

    ActionContext(Action action, Record before, Map<String, Object> values, Map<String, Object> input, Instant now,
                  List<String> events, RecordRepository records) {
        this.action = action;
        this.before = before;
        this.values = values;
        this.input = input;
        this.now = now;
        this.events = events;
        this.records = records;
    }

    /**
     * The action being run.
     *
     * @return the action
     */
    public Action action() {
        return action;
    }

    /**
     * The record as stored before the action started.
     *
     * @return the record, or empty for a creating action
     */
    public Optional<Record> before() {
        return Optional.ofNullable(before);
    }

    /**
     * The record's values so far, including changes made by earlier steps. Put a value to set a field (any type
     * {@link io.joshuasalcedo.schema.record.RecordValues} accepts), remove it to clear the field. The result is checked
     * against the model after the last step.
     *
     * @return the values by field name; changeable
     */
    public Map<String, Object> values() {
        return values;
    }

    /**
     * The action's input, already checked and converted to the record types.
     *
     * @return the input values by field name
     */
    public Map<String, Object> input() {
        return input;
    }

    /**
     * The time the action started; the same for every rule and step, so one action sees one moment.
     *
     * @return the start time
     */
    public Instant now() {
        return now;
    }

    /**
     * Publishes an event once the action has saved, after the events of earlier {@code Emit} steps.
     *
     * @param event the event name, e.g. {@code customer.refunded}
     */
    public void emit(String event) {
        events.add(event);
    }

    /**
     * Reads another record as it is currently stored.
     *
     * @param model the model ID
     * @param id    the record ID
     * @return the record, or empty if there is none
     */
    public Optional<Record> find(String model, String id) {
        return records.find(model, id);
    }

    /**
     * Stops the action: nothing is saved or published, and the caller gets a
     * {@link SchemaException.Reason#RULE_FAILED} error. Declared to return the exception so a handler can write
     * {@code throw ctx.fail("...")} where Java needs a throw; it always throws itself.
     *
     * @param message what the caller is told
     * @return never returns
     */
    public SchemaException fail(String message) {
        throw new SchemaException(SchemaException.Reason.RULE_FAILED, "action." + action.name(), message);
    }
}
