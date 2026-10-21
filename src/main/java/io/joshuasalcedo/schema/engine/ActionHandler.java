package io.joshuasalcedo.schema.engine;

/**
 * Java code an action calls by name ({@link io.joshuasalcedo.schema.action.Step.Call}), for logic too big for an expression:
 * calling another system, a calculation, a lookup. Register it on the engine; it changes the record through
 * {@link ActionContext#values()} and can refuse with {@link ActionContext#fail(String)}.
 */
@FunctionalInterface
public interface ActionHandler {

    /**
     * Does the handler's part of an action. Runs inside the action: it may read and change the record's values,
     * emit events and fail the action, and it must not keep the context after it returns. Any exception it throws
     * stops the action and reaches the caller; nothing is saved.
     *
     * @param context the running action
     */
    void handle(ActionContext context);
}
