package io.joshuasalcedo.schema.port.out;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.action.Action;

/**
 * Outbound port: where action definitions are kept. The library ships
 * {@link io.joshuasalcedo.schema.adapter.InMemoryActionRepository}. Actions arrive here already checked; the repository only
 * stores them.
 */
public interface ActionRepository {

    /**
     * All actions.
     *
     * @return the actions in a stable order, e.g. creation order
     */
    List<Action> findAll();

    /**
     * Looks up an action.
     *
     * @param name the action's name
     * @return the action, or empty if there is none
     */
    Optional<Action> find(String name);

    /**
     * Inserts the action, or replaces the one with the same name.
     *
     * @param action the action to store
     */
    void save(Action action);

    /**
     * Removes an action; does nothing if there is none with that name.
     *
     * @param name the action's name
     */
    void delete(String name);
}
