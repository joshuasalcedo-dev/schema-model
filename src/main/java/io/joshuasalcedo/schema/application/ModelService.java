package io.joshuasalcedo.schema.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.joshuasalcedo.schema.Finding;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaAnalysis;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.SchemaValidator;
import io.joshuasalcedo.schema.Vocabulary;
import io.joshuasalcedo.schema.adapter.InMemoryModelRepository;
import io.joshuasalcedo.schema.port.in.ModelDesign;
import io.joshuasalcedo.schema.port.out.ModelEvents;
import io.joshuasalcedo.schema.port.out.ModelRepository;
import io.joshuasalcedo.schema.port.out.VocabularyProvider;

/**
 * The model use cases over the outbound ports:
 * each change is checked against the schema as stored, then saved, then announced. Changes are serialized so two
 * concurrent writes cannot both pass validation against the same old schema.
 * <pre>{@code
 * ModelDesign design = ModelService.inMemory();
 * design.createModel(Model.valueObject("Address").field(Field.text("street")).build());
 * }</pre>
 */
public final class ModelService implements ModelDesign {

    private final ModelRepository repository;
    private final VocabularyProvider vocabulary;
    private final ModelEvents events;

    /**
     * A model service over the given ports.
     *
     * @param repository where models are kept
     * @param vocabulary the terms models may point to, read for each change
     * @param events     told after each successful change
     */
    public ModelService(ModelRepository repository, VocabularyProvider vocabulary, ModelEvents events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * A model service that accepts any term and publishes no events.
     *
     * @param repository where models are kept
     */
    public ModelService(ModelRepository repository) {
        this(repository, Vocabulary::open, ModelEvents.none());
    }

    /**
     * A model service that keeps models in memory, accepts any term and publishes no events.
     *
     * @return a new, empty service
     */
    public static ModelService inMemory() {
        return new ModelService(new InMemoryModelRepository());
    }

    @Override
    public synchronized Model createModel(Model model) {
        Schema schema = schema();
        if (schema.contains(model.id())) {
            throw new SchemaException(SchemaException.Reason.ALREADY_EXISTS, model.id(),
                                      "model '" + model.id() + "' already exists");
        }
        SchemaValidator.requireValid(model, schema);
        repository.save(model);
        events.created(model);
        return model;
    }

    @Override
    public synchronized Model updateModel(Model model) {
        Schema schema = schema();
        Model before = schema.requireModel(model.id());
        SchemaValidator.requireValid(model, schema);
        repository.save(model);
        events.updated(before, model);
        return model;
    }

    @Override
    public synchronized void deleteModel(String id) {
        Schema schema = schema();
        Model stored = schema.requireModel(id);
        schema.without(id); // refuses while another model's field holds it
        repository.delete(id);
        events.deleted(stored);
    }

    @Override
    public Optional<Model> getModel(String id) {
        return repository.findById(id);
    }

    @Override
    public List<Model> listModels() {
        return repository.findAll();
    }

    @Override
    public Schema schema() {
        return Schema.builder().vocabulary(vocabulary.current()).models(repository.findAll()).buildUnchecked();
    }

    @Override
    public List<SchemaProblem> check(Model model) {
        return SchemaValidator.validate(model, schema());
    }

    @Override
    public List<Finding> analyze() {
        return SchemaAnalysis.analyze(schema());
    }
}
