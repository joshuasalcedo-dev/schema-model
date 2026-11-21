package io.joshuasalcedo.schema.port.out;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.action.DecisionTable;

/**
 * Outbound port: where decision tables are kept. The library ships
 * {@link io.joshuasalcedo.schema.adapter.InMemoryDecisionRepository}; an application implements this over its
 * database (a table with each one as JSON, for example). They arrive here already checked; the repository only
 * stores them.
 */
public interface DecisionRepository {

    /**
     * All of them.
     *
     * @return in a stable order, e.g. creation order
     */
    List<DecisionTable> findAll();

    /**
     * One by its ID.
     *
     * @param id the ID
     * @return it, or empty
     */
    Optional<DecisionTable> findById(String id);

    /**
     * Stores it, replacing the one with the same ID.
     *
     * @param value the checked value
     */
    void save(DecisionTable value);

    /**
     * Removes it; nothing happens when it does not exist.
     *
     * @param id the ID
     */
    void delete(String id);
}
