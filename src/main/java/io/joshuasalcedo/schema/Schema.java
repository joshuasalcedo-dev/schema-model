package io.joshuasalcedo.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A set of models that refer to each other by ID, plus the vocabulary their terms come from. A field of one model
 * can hold another model only if both are in the same schema.
 * <pre>{@code
 * Schema schema = Schema.builder()
 *         .model(Model.valueObject("Address").field(Field.text("street").required()))
 *         .model(Model.entity("Customer")
 *                 .field(Field.text("id").identity())
 *                 .field(Field.model("billingAddress", "address")))
 *         .build();   // throws SchemaException listing every problem
 * }</pre>
 * A schema is immutable: {@link #with} and {@link #without} return a new one.
 */
public final class Schema {

    private final Map<String, Model> models;
    private final Vocabulary vocabulary;

    private Schema(Map<String, Model> models, Vocabulary vocabulary) {
        this.models = models;
        this.vocabulary = vocabulary;
    }

    /**
     * Starts building a schema.
     *
     * @return an empty builder with an {@link Vocabulary#open() open} vocabulary
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A schema with no models that accepts any term.
     *
     * @return the empty schema
     */
    public static Schema empty() {
        return new Schema(Map.of(), Vocabulary.open());
    }

    /**
     * All models.
     *
     * @return the models in the order they were added; unmodifiable
     */
    public Collection<Model> models() {
        return models.values();
    }

    /**
     * Looks up a model.
     *
     * @param id the model ID
     * @return the model, or empty if there is none with that ID
     */
    public Optional<Model> model(String id) {
        return Optional.ofNullable(models.get(id));
    }

    /**
     * Looks up a model that must exist.
     *
     * @param id the model ID
     * @return the model
     * @throws SchemaException with {@link SchemaException.Reason#NOT_FOUND} if there is none with that ID
     */
    public Model requireModel(String id) {
        Model model = models.get(id);
        if (model == null) {
            throw SchemaException.notFound(id);
        }
        return model;
    }

    /**
     * Whether a model with this ID exists.
     *
     * @param id the model ID
     * @return true if the schema has it
     */
    public boolean contains(String id) {
        return models.containsKey(id);
    }

    /**
     * The terms models and fields may point to.
     *
     * @return the vocabulary
     */
    public Vocabulary vocabulary() {
        return vocabulary;
    }

    /**
     * This schema with {@code model} added, or replacing the model with the same ID. The model is checked against
     * the rest of the schema first.
     *
     * @param model the model to add or replace
     * @return a new schema; this one is unchanged
     * @throws SchemaException with {@link SchemaException.Reason#INVALID} and every problem if the model breaks a
     *                         rule
     */
    public Schema with(Model model) {
        SchemaValidator.requireValid(model, this);
        Map<String, Model> copy = new LinkedHashMap<>(models);
        copy.put(model.id(), model);
        return new Schema(Collections.unmodifiableMap(copy), vocabulary);
    }

    /**
     * This schema without a model.
     *
     * @param id the ID of the model to remove
     * @return a new schema; this one is unchanged
     * @throws SchemaException with {@link SchemaException.Reason#NOT_FOUND} if there is no such model, or
     *                         {@link SchemaException.Reason#IN_USE} while another model's field still holds it
     */
    public Schema without(String id) {
        requireModel(id);
        List<String> usedBy = usedBy(id);
        if (!usedBy.isEmpty()) {
            throw new SchemaException(SchemaException.Reason.IN_USE, id, "model '" + id + "' is used by "
                                      + String.join(", ", usedBy) + "; change those fields first");
        }
        Map<String, Model> copy = new LinkedHashMap<>(models);
        copy.remove(id);
        return new Schema(Collections.unmodifiableMap(copy), vocabulary);
    }

    /**
     * The other models with a field that holds model {@code id}; a model that holds itself is not counted.
     *
     * @param id the model ID
     * @return their IDs, in schema order; empty when nothing uses it
     */
    public List<String> usedBy(String id) {
        return models.values().stream()
                .filter(m -> !m.id().equals(id))
                .filter(m -> m.fields().stream().anyMatch(f -> f.type() == FieldType.MODEL && f.model().equals(id)))
                .map(Model::id)
                .toList();
    }

    /**
     * Every glossary term a model or field points to, e.g. to find terms that nothing uses.
     *
     * @return the terms, in the order first used
     */
    public Set<String> usedTerms() {
        Set<String> used = new LinkedHashSet<>();
        for (Model model : models.values()) {
            if (!model.term().isEmpty()) {
                used.add(model.term());
            }
            for (Field field : model.fields()) {
                if (!field.term().isEmpty()) {
                    used.add(field.term());
                }
            }
        }
        return used;
    }

    /**
     * Checks every model; the same as {@link SchemaValidator#validate(Schema)}.
     *
     * @return every problem; empty when the schema is valid
     */
    public List<SchemaProblem> validate() {
        return SchemaValidator.validate(this);
    }

    /**
     * Looks for quality issues; the same as {@link SchemaAnalysis#analyze(Schema)}.
     *
     * @return the findings; empty when there is nothing to improve
     */
    public List<Finding> analyze() {
        return SchemaAnalysis.analyze(this);
    }

    /** Builds a {@link Schema} from models; checks them all at the end. */
    public static final class Builder {
        private final Map<String, Model> models = new LinkedHashMap<>();
        private final List<SchemaProblem> duplicates = new ArrayList<>();
        private Vocabulary vocabulary = Vocabulary.open();

        private Builder() {
        }

        /**
         * Sets the terms models and fields may point to.
         *
         * @param vocabulary the vocabulary; {@link Vocabulary#open()} by default
         * @return this builder
         */
        public Builder vocabulary(Vocabulary vocabulary) {
            this.vocabulary = vocabulary;
            return this;
        }

        /**
         * Adds a model. A second model with the same ID is reported by {@link #build()}; the first one is kept.
         *
         * @param model the model
         * @return this builder
         */
        public Builder model(Model model) {
            if (models.putIfAbsent(model.id(), model) != null) {
                duplicates.add(new SchemaProblem("models[" + model.id() + "]",
                                                 "models[" + model.id() + "] the ID '" + model.id() + "' is used twice"));
            }
            return this;
        }

        /**
         * Adds a model, building it first.
         *
         * @param model the model's builder
         * @return this builder
         */
        public Builder model(Model.Builder model) {
            return model(model.build());
        }

        /**
         * Adds several models, in order.
         *
         * @param models the models
         * @return this builder
         */
        public Builder models(Collection<Model> models) {
            models.forEach(this::model);
            return this;
        }

        /**
         * Builds and checks the schema.
         *
         * @return the schema
         * @throws SchemaException with {@link SchemaException.Reason#INVALID} listing every problem, including IDs
         *                         used twice
         */
        public Schema build() {
            Schema schema = buildUnchecked();
            List<SchemaProblem> problems = new ArrayList<>(duplicates);
            problems.addAll(SchemaValidator.validate(schema));
            if (!problems.isEmpty()) {
                throw new SchemaException(problems);
            }
            return schema;
        }

        /**
         * Builds without checking, e.g. to inspect a design that is still in progress or to load models that were
         * already checked when they were saved.
         *
         * @return the schema, valid or not
         */
        public Schema buildUnchecked() {
            return new Schema(Collections.unmodifiableMap(new LinkedHashMap<>(models)), vocabulary);
        }
    }
}
