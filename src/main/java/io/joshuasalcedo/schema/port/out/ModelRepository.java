package io.joshuasalcedo.schema.port.out;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.Model;

/**
 * Outbound port: where models are kept. The library ships
 * {@link io.joshuasalcedo.schema.adapter.InMemoryModelRepository}; a service implements this over its database (the knowledge
 * service's {@code models} table, for example). Models arrive here already checked; the repository only stores
 * them.
 */
public interface ModelRepository {

    /**
     * All models.
     *
     * @return the models in a stable order, e.g. creation order
     */
    List<Model> findAll();

    /**
     * Looks up a model.
     *
     * @param id the model ID
     * @return the model, or empty if there is none
     */
    Optional<Model> findById(String id);

    /**
     * Inserts the model, or replaces the one with the same ID.
     *
     * @param model the model to store
     */
    void save(Model model);

    /**
     * Removes a model; does nothing if there is none with that ID.
     *
     * @param id the model ID
     */
    void delete(String id);
}
