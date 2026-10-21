package io.joshuasalcedo.schema.port.in;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.Finding;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaProblem;

/**
 * Inbound port: what an application can do with its models. Implemented by
 * {@link io.joshuasalcedo.schema.application.ModelService}, and guarded further by the engine (see
 * {@link Platform#models()}). Call it from a controller, a gRPC service, a CLI or a test.
 * <p>
 * Refusals are {@link io.joshuasalcedo.schema.SchemaException}s whose {@code reason()} says why.
 */
public interface ModelDesign {

    /**
     * Adds a new model.
     *
     * @param model the model; its fields may hold models that already exist, or the model itself
     * @return the saved model
     * @throws io.joshuasalcedo.schema.SchemaException ALREADY_EXISTS if the ID is taken, INVALID if it breaks a rule
     */
    Model createModel(Model model);

    /**
     * Replaces the model with the same ID. Records already stored are not changed; each is checked against the new
     * model the next time it is saved.
     *
     * @param model the new version of the model
     * @return the saved model
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND if there is no model with that ID, INVALID if it breaks a rule
     */
    Model updateModel(Model model);

    /**
     * Removes a model.
     *
     * @param id the model ID
     * @throws io.joshuasalcedo.schema.SchemaException NOT_FOUND if there is no such model, IN_USE while another model's field
     *                                       holds it
     */
    void deleteModel(String id);

    /**
     * Looks up a model.
     *
     * @param id the model ID
     * @return the model, or empty if there is none
     */
    Optional<Model> getModel(String id);

    /**
     * All models.
     *
     * @return the models in the repository's order
     */
    List<Model> listModels();

    /**
     * Every model, with the current vocabulary, as one schema.
     *
     * @return the schema as stored now
     */
    Schema schema();

    /**
     * The problems {@code model} would have if it were saved now; nothing is saved.
     *
     * @param model the model to check
     * @return every problem; empty when it is valid
     */
    List<SchemaProblem> check(Model model);

    /**
     * Quality findings for every model.
     *
     * @return the findings; empty when there is nothing to improve
     */
    List<Finding> analyze();
}
