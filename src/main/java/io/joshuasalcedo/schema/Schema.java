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
    private final Map<String, Taxonomy> taxonomies;
    private final Map<String, io.joshuasalcedo.schema.action.DecisionTable> decisions;
    private Map<String, Lifecycle> lifecycles = Map.of();

    private Schema(Map<String, Model> models, Vocabulary vocabulary) {
        this(models, vocabulary, Map.of(), Map.of());
    }

    private Schema(Map<String, Model> models, Vocabulary vocabulary, Map<String, Taxonomy> taxonomies,
                   Map<String, io.joshuasalcedo.schema.action.DecisionTable> decisions) {
        this.models = models;
        this.vocabulary = vocabulary;
        this.taxonomies = taxonomies;
        this.decisions = decisions;
    }

    /**
     * The taxonomies, by ID: the classifications category fields take their terms from.
     *
     * @return the taxonomies, unmodifiable
     */
    public Collection<Taxonomy> taxonomies() {
        return taxonomies.values();
    }

    /**
     * A taxonomy by its ID.
     *
     * @param id the ID
     * @return the taxonomy, or empty
     */
    public Optional<Taxonomy> taxonomy(String id) {
        return Optional.ofNullable(taxonomies.get(id));
    }

    /**
     * The decision tables, by ID.
     *
     * @return the tables, unmodifiable
     */
    public Collection<io.joshuasalcedo.schema.action.DecisionTable> decisions() {
        return decisions.values();
    }

    /**
     * A decision table by its ID.
     *
     * @param id the ID
     * @return the table, or empty
     */
    public Optional<io.joshuasalcedo.schema.action.DecisionTable> decision(String id) {
        return Optional.ofNullable(decisions.get(id));
    }

    /**
     * The lifecycles, by ID: the states records go through and the moves allowed between them.
     *
     * @return the lifecycles, unmodifiable
     */
    public Collection<Lifecycle> lifecycles() {
        return lifecycles.values();
    }

    /**
     * A lifecycle by its ID.
     *
     * @param id the ID
     * @return the lifecycle, or empty
     */
    public Optional<Lifecycle> lifecycle(String id) {
        return Optional.ofNullable(lifecycles.get(id));
    }

    /**
     * This schema with a lifecycle added or replaced.
     *
     * @param lifecycle the lifecycle
     * @return a new schema
     * @throws SchemaException if the lifecycle is invalid
     */
    public Schema with(Lifecycle lifecycle) {
        List<SchemaProblem> problems = lifecycle.validate();
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
        Map<String, Lifecycle> copy = new LinkedHashMap<>(lifecycles);
        copy.put(lifecycle.id(), lifecycle);
        return withLifecycles(Collections.unmodifiableMap(copy));
    }

    /**
     * This schema without a lifecycle.
     *
     * @param id the lifecycle's ID
     * @return a new schema
     * @throws SchemaException if it does not exist
     */
    public Schema withoutLifecycle(String id) {
        if (!lifecycles.containsKey(id)) {
            throw SchemaException.notFound(id);
        }
        Map<String, Lifecycle> copy = new LinkedHashMap<>(lifecycles);
        copy.remove(id);
        return withLifecycles(Collections.unmodifiableMap(copy));
    }

    private Schema withLifecycles(Map<String, Lifecycle> next) {
        Schema s = new Schema(models, vocabulary, taxonomies, decisions);
        s.lifecycles = next;
        return s;
    }

    private Schema keepLifecycles(Schema s) {
        s.lifecycles = this.lifecycles;
        return s;
    }

    /**
     * This schema with a taxonomy added or replaced.
     *
     * @param taxonomy the taxonomy
     * @return a new schema
     * @throws SchemaException if the taxonomy is invalid
     */
    public Schema with(Taxonomy taxonomy) {
        List<SchemaProblem> problems = taxonomy.validate();
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
        Map<String, Taxonomy> copy = new LinkedHashMap<>(taxonomies);
        copy.put(taxonomy.id(), taxonomy);
        return keepLifecycles(new Schema(models, vocabulary, Collections.unmodifiableMap(copy), decisions));
    }

    /**
     * This schema with a decision table added or replaced.
     *
     * @param decision the table
     * @return a new schema
     * @throws SchemaException if the table is invalid
     */
    public Schema with(io.joshuasalcedo.schema.action.DecisionTable decision) {
        List<SchemaProblem> problems = decision.validate();
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
        Map<String, io.joshuasalcedo.schema.action.DecisionTable> copy = new LinkedHashMap<>(decisions);
        copy.put(decision.id(), decision);
        return keepLifecycles(new Schema(models, vocabulary, taxonomies, Collections.unmodifiableMap(copy)));
    }

    /**
     * This schema without a taxonomy.
     *
     * @param id the taxonomy's ID
     * @return a new schema
     * @throws SchemaException if it does not exist, or a field still takes its terms
     */
    public Schema withoutTaxonomy(String id) {
        if (!taxonomies.containsKey(id)) {
            throw SchemaException.notFound(id);
        }
        List<String> usedBy = models.values().stream()
                .filter(m -> m.fields().stream().anyMatch(f -> f.taxonomy().equals(id)))
                .map(Model::id).toList();
        if (!usedBy.isEmpty()) {
            throw new SchemaException(SchemaException.Reason.IN_USE, id, "taxonomy '" + id + "' is used by "
                                      + String.join(", ", usedBy) + "; change those fields first");
        }
        Map<String, Taxonomy> copy = new LinkedHashMap<>(taxonomies);
        copy.remove(id);
        return keepLifecycles(new Schema(models, vocabulary, Collections.unmodifiableMap(copy), decisions));
    }

    /**
     * This schema without a decision table.
     *
     * @param id the table's ID
     * @return a new schema
     * @throws SchemaException if it does not exist
     */
    public Schema withoutDecision(String id) {
        if (!decisions.containsKey(id)) {
            throw SchemaException.notFound(id);
        }
        Map<String, io.joshuasalcedo.schema.action.DecisionTable> copy = new LinkedHashMap<>(decisions);
        copy.remove(id);
        return keepLifecycles(new Schema(models, vocabulary, taxonomies, Collections.unmodifiableMap(copy)));
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
        return keepLifecycles(new Schema(Collections.unmodifiableMap(copy), vocabulary, taxonomies, decisions));
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
        return keepLifecycles(new Schema(Collections.unmodifiableMap(copy), vocabulary, taxonomies, decisions));
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
        private final Map<String, Taxonomy> taxonomies = new LinkedHashMap<>();
        private final Map<String, io.joshuasalcedo.schema.action.DecisionTable> decisions = new LinkedHashMap<>();
        private final Map<String, Lifecycle> lifecycles = new LinkedHashMap<>();

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
        /**
         * Adds a taxonomy.
         *
         * @param taxonomy the taxonomy
         * @return this builder
         */
        public Builder taxonomy(Taxonomy taxonomy) {
            if (taxonomies.putIfAbsent(taxonomy.id(), taxonomy) != null) {
                duplicates.add(new SchemaProblem("taxonomies[" + taxonomy.id() + "]",
                                                 "taxonomies[" + taxonomy.id() + "] the ID '" + taxonomy.id() + "' is used twice"));
            }
            return this;
        }

        /**
         * Adds taxonomies.
         *
         * @param taxonomies the taxonomies
         * @return this builder
         */
        public Builder taxonomies(Collection<Taxonomy> taxonomies) {
            taxonomies.forEach(this::taxonomy);
            return this;
        }

        /**
         * Adds a decision table.
         *
         * @param decision the table
         * @return this builder
         */
        public Builder decision(io.joshuasalcedo.schema.action.DecisionTable decision) {
            if (decisions.putIfAbsent(decision.id(), decision) != null) {
                duplicates.add(new SchemaProblem("decisions[" + decision.id() + "]",
                                                 "decisions[" + decision.id() + "] the ID '" + decision.id() + "' is used twice"));
            }
            return this;
        }

        /**
         * Adds decision tables.
         *
         * @param decisions the tables
         * @return this builder
         */
        public Builder decisions(Collection<io.joshuasalcedo.schema.action.DecisionTable> decisions) {
            decisions.forEach(this::decision);
            return this;
        }

        /**
         * Adds a lifecycle.
         *
         * @param lifecycle the lifecycle
         * @return this builder
         */
        public Builder lifecycle(Lifecycle lifecycle) {
            if (lifecycles.putIfAbsent(lifecycle.id(), lifecycle) != null) {
                duplicates.add(new SchemaProblem("lifecycles[" + lifecycle.id() + "]",
                                                 "lifecycles[" + lifecycle.id() + "] the ID '" + lifecycle.id() + "' is used twice"));
            }
            return this;
        }

        /**
         * Adds lifecycles.
         *
         * @param lifecycles the lifecycles
         * @return this builder
         */
        public Builder lifecycles(Collection<Lifecycle> lifecycles) {
            lifecycles.forEach(this::lifecycle);
            return this;
        }

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
            Schema schema = new Schema(Collections.unmodifiableMap(new LinkedHashMap<>(models)), vocabulary,
                                       Collections.unmodifiableMap(new LinkedHashMap<>(taxonomies)),
                                       Collections.unmodifiableMap(new LinkedHashMap<>(decisions)));
            schema.lifecycles = Collections.unmodifiableMap(new LinkedHashMap<>(lifecycles));
            return schema;
        }
    }
}
