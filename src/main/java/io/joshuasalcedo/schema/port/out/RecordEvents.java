package io.joshuasalcedo.schema.port.out;

import io.joshuasalcedo.schema.record.Record;

/**
 * Outbound port: told after each saved change to a record, e.g. to publish to NATS or write an audit log. Every
 * change publishes {@code {model}.created}, {@code {model}.updated} or {@code {model}.deleted}; actions add their own
 * {@link io.joshuasalcedo.schema.action.Step.Emit} events after it. Nothing is published for a change that failed.
 */
@FunctionalInterface
public interface RecordEvents {

    /**
     * One event about one record.
     *
     * @param name   e.g. {@code customer.updated} or {@code customer.closed}
     * @param action the name of the action that made the change, or empty for a plain create, update or delete
     * @param before the record before the change; null when it was created
     * @param after  the record after the change; null when it was deleted
     */
    record RecordEvent(String name, String action, Record before, Record after) {
    }

    /**
     * Handles one event. Called after the change is saved, on the calling thread; an exception here reaches the
     * caller although the change is already saved, so slow or unreliable work belongs on a queue.
     *
     * @param event the event
     */
    void publish(RecordEvent event);

    /**
     * Ignores every event.
     *
     * @return a no-op publisher
     */
    static RecordEvents none() {
        return event -> {
        };
    }
}
