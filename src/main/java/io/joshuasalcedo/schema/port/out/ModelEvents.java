package io.joshuasalcedo.schema.port.out;

import io.joshuasalcedo.schema.Model;

/**
 * Outbound port: told after each successful change to a model, e.g. to publish to NATS or write an audit log.
 * Nothing is called for a change that was refused.
 */
public interface ModelEvents {

    /**
     * A model was added.
     *
     * @param model the new model
     */
    void created(Model model);

    /**
     * A model was replaced.
     *
     * @param before the model as it was
     * @param after  the model as it is now
     */
    void updated(Model before, Model after);

    /**
     * A model was removed.
     *
     * @param model the model as it was
     */
    void deleted(Model model);

    /**
     * Ignores every change.
     *
     * @return a no-op listener
     */
    static ModelEvents none() {
        return new ModelEvents() {
            @Override
            public void created(Model model) {
            }

            @Override
            public void updated(Model before, Model after) {
            }

            @Override
            public void deleted(Model model) {
            }
        };
    }
}
