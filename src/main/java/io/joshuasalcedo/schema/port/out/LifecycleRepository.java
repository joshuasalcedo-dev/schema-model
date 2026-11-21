package io.joshuasalcedo.schema.port.out;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.Lifecycle;

/**
 * Outbound port: where lifecycles are kept. The library ships
 * {@link io.joshuasalcedo.schema.adapter.InMemoryLifecycleRepository}; an application implements this over its
 * database. They arrive here already checked; the repository only stores them.
 */
public interface LifecycleRepository {

    /**
     * All of them.
     *
     * @return in a stable order, e.g. creation order
     */
    List<Lifecycle> findAll();

    /**
     * One by its ID.
     *
     * @param id the ID
     * @return it, or empty
     */
    Optional<Lifecycle> findById(String id);

    /**
     * Stores it, replacing the one with the same ID.
     *
     * @param value the checked value
     */
    void save(Lifecycle value);

    /**
     * Removes it; nothing happens when it does not exist.
     *
     * @param id the ID
     */
    void delete(String id);
}
