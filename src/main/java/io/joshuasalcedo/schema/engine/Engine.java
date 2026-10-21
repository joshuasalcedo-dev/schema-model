package io.joshuasalcedo.schema.engine;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Finding;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.action.Action;
import io.joshuasalcedo.schema.action.ActionValidator;
import io.joshuasalcedo.schema.action.Expressions;
import io.joshuasalcedo.schema.action.Rule;
import io.joshuasalcedo.schema.action.Step;
import io.joshuasalcedo.schema.adapter.InMemoryActionRepository;
import io.joshuasalcedo.schema.adapter.InMemoryRecordRepository;
import io.joshuasalcedo.schema.application.ModelService;
import io.joshuasalcedo.schema.port.in.ModelDesign;
import io.joshuasalcedo.schema.port.in.Platform;
import io.joshuasalcedo.schema.port.out.ActionRepository;
import io.joshuasalcedo.schema.port.out.RecordEvents;
import io.joshuasalcedo.schema.port.out.RecordEvents.RecordEvent;
import io.joshuasalcedo.schema.port.out.RecordRepository;
import io.joshuasalcedo.schema.record.Record;
import io.joshuasalcedo.schema.record.RecordValues;

/**
 * The one engine that runs everything defined through {@link Platform}. Calls are serialized within an engine;
 * record versions protect against other engines writing the same data.
 * <pre>{@code
 * Engine engine = Engine.builder()
 *         .handler("billing.refund", ctx -> ctx.values().put("refunded", true))
 *         .events(event -> nats.publish(event.name(), ...))
 *         .build();                      // in memory unless repositories are given
 * engine.models().createModel(...);
 * engine.defineAction(...);
 * engine.run("CloseAccount", "c-123", Map.of("reason", "moved away"));
 * }</pre>
 */
public final class Engine implements Platform {

    private final ModelDesign models;
    private final RecordRepository records;
    private final ActionRepository actions;
    private final RecordEvents events;
    private final io.joshuasalcedo.schema.port.out.FileStore files;
    private final long maxFileBytes;
    private final Map<String, ActionHandler> handlers;
    private final Clock clock;
    private final Expressions expressions = new Expressions();
    private final ModelDesign guardedModels;

    private Engine(Builder b) {
        this.models = b.models;
        this.records = b.records;
        this.actions = b.actions;
        this.events = b.events;
        this.files = b.files;
        this.maxFileBytes = b.maxFileBytes;
        this.handlers = Map.copyOf(b.handlers);
        this.clock = b.clock;
        this.guardedModels = new GuardedModels();
    }

    /**
     * Starts configuring an engine. Every port defaults to an in-memory or no-op adapter, so
     * {@code Engine.builder().build()} is a working platform.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelDesign models() {
        return guardedModels;
    }

    // --- actions -------------------------------------------------------------------------------------------

    @Override
    public synchronized Action defineAction(Action action) {
        List<SchemaProblem> problems = ActionValidator.validate(action, models.schema(), handlers.keySet(),
                                                                expressions);
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
        actions.save(action);
        return action;
    }

    @Override
    public synchronized void removeAction(String name) {
        if (actions.find(name).isEmpty()) {
            throw new SchemaException(SchemaException.Reason.NOT_FOUND, name, "action '" + name + "' was not found");
        }
        actions.delete(name);
    }

    @Override
    public Optional<Action> action(String name) {
        return actions.find(name);
    }

    @Override
    public List<Action> actions() {
        return actions.findAll();
    }

    @Override
    public synchronized Record run(String actionName, String recordId, Map<String, ?> rawInput) {
        Action action = actions.find(actionName).orElseThrow(
                () -> new SchemaException(SchemaException.Reason.NOT_FOUND, actionName,
                                          "action '" + actionName + "' was not found"));
        Schema schema = models.schema();
        Model model = schema.requireModel(action.model());

        RecordValues.Result input = RecordValues.convert(ActionValidator.inputModel(action), schema,
                                                         rawInput == null ? Map.of() : rawInput, "input",
                                                         clock.instant());
        requireValid(input);
        requireReferencesExist(input);

        Record before = null;
        if (action.target() == Action.Target.EXISTING) {
            if (recordId == null || recordId.isBlank()) {
                throw new SchemaException(SchemaException.Reason.INVALID, "recordId",
                                          "action '" + actionName + "' runs on an existing " + model.displayName()
                                          + "; give its ID");
            }
            before = records.find(model.id(), recordId).orElseThrow(() -> recordNotFound(model.id(), recordId));
        }

        Instant now = clock.instant();
        Map<String, Object> values = new LinkedHashMap<>(before == null ? Map.of() : before.values());
        List<String> emitted = new ArrayList<>();
        Map<String, Object> linked = linked(model, values, ActionValidator.inputModel(action), input.values());

        for (int i = 0; i < action.rules().size(); i++) {
            Rule rule = action.rules().get(i);
            Object result = evaluate(rule.expression(), values, input.values(), linked, now,
                                     "action.rules[" + i + "]");
            if (!Boolean.TRUE.equals(result)) {
                throw new SchemaException(SchemaException.Reason.RULE_FAILED, "action.rules[" + i + "]",
                                          rule.message());
            }
        }

        ActionContext context = new ActionContext(action, before, values, input.values(), now, emitted, records);
        for (int i = 0; i < action.steps().size(); i++) {
            switch (action.steps().get(i)) {
                case Step.Set set -> {
                    Object result = evaluate(set.expression(), values, input.values(), linked, now,
                                             "action.steps[" + i + "]");
                    if (isNull(result)) {
                        values.remove(set.field());
                    } else {
                        values.put(set.field(), result);
                    }
                }
                case Step.Call call -> {
                    ActionHandler handler = handlers.get(call.handler());
                    if (handler == null) {
                        throw new SchemaException(SchemaException.Reason.INVALID, "action.steps[" + i + "].handler",
                                                  "no handler is registered as '" + call.handler() + "'");
                    }
                    handler.handle(context);
                }
                case Step.Emit emit -> emitted.add(emit.event());
            }
        }

        Record after = store(model, schema, before, values);
        String change = before == null ? "created" : "updated";
        events.publish(new RecordEvent(model.id() + "." + change, action.name(), before, after));
        for (String event : emitted) {
            events.publish(new RecordEvent(event, action.name(), before, after));
        }
        return after;
    }

    // --- records -------------------------------------------------------------------------------------------

    @Override
    public synchronized Record create(String modelId, Map<String, ?> values) {
        Schema schema = models.schema();
        Model model = schema.requireModel(modelId);
        Record created = store(model, schema, null, values);
        events.publish(new RecordEvent(model.id() + ".created", "", null, created));
        return created;
    }

    @Override
    public synchronized Record update(String modelId, String id, Map<String, ?> changes, long expectedVersion) {
        Schema schema = models.schema();
        Model model = schema.requireModel(modelId);
        Record before = records.find(modelId, id).orElseThrow(() -> recordNotFound(modelId, id));
        if (expectedVersion > 0 && expectedVersion != before.version()) {
            throw conflict(before);
        }
        Map<String, Object> merged = new HashMap<>(before.values());
        changes.forEach((field, value) -> {
            if (value == null) {
                merged.remove(field);
            } else {
                merged.put(field, value);
            }
        });
        Record after = store(model, schema, before, merged);
        events.publish(new RecordEvent(model.id() + ".updated", "", before, after));
        return after;
    }

    @Override
    public synchronized void delete(String modelId, String id) {
        Schema schema = models.schema();
        Record stored = records.find(modelId, id).orElseThrow(() -> recordNotFound(modelId, id));
        List<String> referrers = referrers(schema, modelId, id);
        if (!referrers.isEmpty()) {
            throw new SchemaException(SchemaException.Reason.IN_USE, stored.key(),
                                      stored.key() + " is referred to by " + String.join(", ", referrers));
        }
        if (!records.delete(modelId, id, stored.version())) {
            throw conflict(stored);
        }
        events.publish(new RecordEvent(modelId + ".deleted", "", stored, null));
    }

    @Override
    public Optional<Record> get(String model, String id) {
        return records.find(model, id);
    }

    @Override
    public List<Record> list(String model) {
        return records.findAll(model);
    }

    // --- files ---------------------------------------------------------------------------------------------

    @Override
    public io.joshuasalcedo.schema.file.FileRef upload(String name, String contentType, java.io.InputStream content) {
        if (name == null || name.isBlank()) {
            throw new SchemaException(SchemaException.Reason.INVALID, "file.name", "file.name is required");
        }
        java.io.InputStream limited = new java.io.FilterInputStream(content) {
            private long read;

            private void count(long n) {
                read += Math.max(0, n);
                if (read > maxFileBytes) {
                    throw new SchemaException(SchemaException.Reason.INVALID, "file",
                                              "file is larger than " + maxFileBytes + " bytes");
                }
            }

            @Override
            public int read() throws java.io.IOException {
                int b = super.read();
                count(b < 0 ? 0 : 1);
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws java.io.IOException {
                int n = super.read(b, off, len);
                count(n);
                return n;
            }
        };
        return files.put(name.strip(), contentType, limited);
    }

    @Override
    public Optional<java.io.InputStream> download(String location) {
        return files.open(location);
    }

    /**
     * Refuses values that point to files that are not stored: every {@code file} value, also inside value objects
     * (an {@code image}'s file) and lists.
     */
    private void requireFilesStored(Model model, Schema schema, Map<String, Object> values, String at) {
        List<SchemaProblem> missing = new ArrayList<>();
        collectFiles(model, schema, values, at, missing);
        if (!missing.isEmpty()) {
            throw new SchemaException(missing);
        }
    }

    private void collectFiles(Model model, Schema schema, Map<?, ?> values, String at, List<SchemaProblem> missing) {
        for (io.joshuasalcedo.schema.Field field : model.fields()) {
            if (field.type() != io.joshuasalcedo.schema.FieldType.MODEL || values.get(field.name()) == null) {
                continue;
            }
            Model target = schema.model(field.model()).orElse(null);
            if (target == null || target.isEntity()) {
                continue;
            }
            Object value = values.get(field.name());
            List<?> items = value instanceof List<?> list ? list : List.of(value);
            for (int i = 0; i < items.size(); i++) {
                String path = at + "." + field.name() + (value instanceof List<?> ? "[" + i + "]" : "");
                if (!(items.get(i) instanceof Map<?, ?> nested)) {
                    continue;
                }
                if (target.id().equals("file")) {
                    Object location = nested.get("location");
                    if (location == null || !files.exists(String.valueOf(location))) {
                        missing.add(new SchemaProblem(path, path + " '" + nested.get("name")
                                                            + "' is not stored; upload it first"));
                    }
                } else {
                    collectFiles(target, schema, nested, path, missing);
                }
            }
        }
    }

    // --- internals -----------------------------------------------------------------------------------------

    /** Checks {@code values} against the model, then saves them as the next version of {@code before}. */
    private Record store(Model model, Schema schema, Record before, Map<String, ?> values) {
        if (!model.isEntity()) {
            throw new SchemaException(SchemaException.Reason.INVALID, model.id(),
                                      model.displayName() + " is a value object; it is stored inside entities, "
                                      + "not on its own");
        }
        RecordValues.Result result = RecordValues.convert(model, schema, values, "record", clock.instant());
        requireValid(result);
        requireReferencesExist(result);
        requireFilesStored(model, schema, result.values(), "record");

        String identity = RecordValues.identity(model, result.values());
        String id;
        if (before == null) {
            id = identity != null ? identity : UUID.randomUUID().toString();
        } else {
            id = before.id();
            if (identity != null && !identity.equals(before.id())) {
                throw new SchemaException(SchemaException.Reason.INVALID, "record",
                                          "identity fields of " + before.key() + " cannot change");
            }
        }
        Record record = new Record(model.id(), id, result.values(), before == null ? 1 : before.version() + 1);
        if (!records.save(record)) {
            if (before == null) {
                throw new SchemaException(SchemaException.Reason.ALREADY_EXISTS, record.key(),
                                          record.key() + " already exists");
            }
            throw conflict(before);
        }
        return record;
    }

    /**
     * The records an action points to, by the field that points (the record's fields, then the input's, so a new
     * plan in the input wins over the record's old one): {@code linked.plan} is the plan's values.
     */
    private Map<String, Object> linked(Model model, Map<String, Object> record, Model inputModel,
                                       Map<String, Object> input) {
        Map<String, Object> linked = new HashMap<>();
        for (var source : List.of(Map.entry(model, record), Map.entry(inputModel, input))) {
            for (Field field : source.getKey().fields()) {
                Object id = source.getValue().get(field.name());
                if (field.type() == FieldType.MODEL && id instanceof String text && !text.isBlank()) {
                    records.find(field.model(), text).ifPresent(r -> linked.put(field.name(), r.values()));
                }
            }
        }
        return linked;
    }

    private Object evaluate(String expression, Map<String, Object> record, Map<String, Object> input,
                            Map<String, Object> linked, Instant now, String at) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("record", RecordValues.toExpressionValue(record));
        variables.put("input", RecordValues.toExpressionValue(input));
        variables.put("linked", RecordValues.toExpressionValue(linked));
        variables.putAll(Expressions.clock(now));
        try {
            return expressions.eval(expression, variables);
        } catch (IllegalArgumentException e) {
            throw new SchemaException(SchemaException.Reason.INVALID, at,
                                      at + " could not be evaluated: " + e.getMessage());
        }
    }

    /** CEL's null is its own type; treat it (and Java null) as "no value". */
    private static boolean isNull(Object value) {
        return value == null || value.getClass().getSimpleName().equals("NullValue");
    }

    private void requireValid(RecordValues.Result result) {
        if (!result.valid()) {
            throw new SchemaException(result.problems());
        }
    }

    private void requireReferencesExist(RecordValues.Result result) {
        List<SchemaProblem> missing = new ArrayList<>();
        for (Map.Entry<String, String> ref : result.references()) {
            if (records.find(ref.getKey(), ref.getValue()).isEmpty()) {
                String key = ref.getKey() + "/" + ref.getValue();
                missing.add(new SchemaProblem(key, key + " does not exist"));
            }
        }
        if (!missing.isEmpty()) {
            throw new SchemaException(missing);
        }
    }

    /**
     * The keys of records that refer to {@code model/id}. Reads every record of every entity model, which is fine
     * in memory; a database adapter with many records should index references instead.
     */
    private List<String> referrers(Schema schema, String model, String id) {
        List<String> out = new ArrayList<>();
        for (Model candidate : schema.models()) {
            if (!candidate.isEntity()) {
                continue;
            }
            for (Record record : records.findAll(candidate.id())) {
                RecordValues.Result r = RecordValues.convert(candidate, schema, record.values(), "record");
                if (r.references().contains(Map.entry(model, id))) {
                    out.add(record.key());
                }
            }
        }
        return out;
    }

    private static SchemaException recordNotFound(String model, String id) {
        return new SchemaException(SchemaException.Reason.NOT_FOUND, model + "/" + id,
                                   model + "/" + id + " was not found");
    }

    private static SchemaException conflict(Record read) {
        return new SchemaException(SchemaException.Reason.CONFLICT, read.key(),
                                   read.key() + " changed since version " + read.version() + "; read it again");
    }

    /**
     * The models as the engine exposes them: deleting a model is also refused while records or actions use it,
     * and an entity with records cannot become a value object.
     */
    private final class GuardedModels implements ModelDesign {

        @Override
        public Model createModel(Model model) {
            return models.createModel(model);
        }

        @Override
        public Model updateModel(Model model) {
            synchronized (Engine.this) {
                if (model.isValueObject() && !records.findAll(model.id()).isEmpty()) {
                    throw new SchemaException(SchemaException.Reason.IN_USE, model.id(),
                                              "'" + model.id() + "' has records; it cannot become a value object");
                }
                return models.updateModel(model);
            }
        }

        @Override
        public void deleteModel(String id) {
            synchronized (Engine.this) {
                if (!records.findAll(id).isEmpty()) {
                    throw new SchemaException(SchemaException.Reason.IN_USE, id,
                                              "model '" + id + "' still has records; delete them first");
                }
                List<String> usedBy = actions.findAll().stream().filter(a -> a.model().equals(id))
                        .map(Action::name).toList();
                if (!usedBy.isEmpty()) {
                    throw new SchemaException(SchemaException.Reason.IN_USE, id,
                                              "model '" + id + "' is used by actions " + String.join(", ", usedBy));
                }
                models.deleteModel(id);
            }
        }

        @Override
        public Optional<Model> getModel(String id) {
            return models.getModel(id);
        }

        @Override
        public List<Model> listModels() {
            return models.listModels();
        }

        @Override
        public Schema schema() {
            return models.schema();
        }

        @Override
        public List<SchemaProblem> check(Model model) {
            return models.check(model);
        }

        @Override
        public List<Finding> analyze() {
            return models.analyze();
        }
    }

    /** Configures an {@link Engine}: which adapter backs each port, and which handlers actions can call. */
    public static final class Builder {
        private ModelDesign models;
        private RecordRepository records = new InMemoryRecordRepository();
        private ActionRepository actions = new InMemoryActionRepository();
        private RecordEvents events = RecordEvents.none();
        private io.joshuasalcedo.schema.port.out.FileStore files = new io.joshuasalcedo.schema.adapter.InMemoryFileStore();
        private long maxFileBytes = 25L * 1024 * 1024;
        private final Map<String, ActionHandler> handlers = new HashMap<>();
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        /**
         * Sets where models live.
         *
         * @param models the model use cases, e.g. a {@link ModelService} over a database repository;
         *               {@link ModelService#inMemory()} by default
         * @return this builder
         */
        public Builder models(ModelDesign models) {
            this.models = models;
            return this;
        }

        /**
         * Sets where records live.
         *
         * @param records the repository; in memory by default
         * @return this builder
         */
        public Builder records(RecordRepository records) {
            this.records = records;
            return this;
        }

        /**
         * Sets where action definitions live.
         *
         * @param actions the repository; in memory by default
         * @return this builder
         */
        public Builder actions(ActionRepository actions) {
            this.actions = actions;
            return this;
        }

        /**
         * Sets where file bytes live.
         *
         * @param files the store; in memory by default
         * @return this builder
         */
        public Builder files(io.joshuasalcedo.schema.port.out.FileStore files) {
            this.files = files;
            return this;
        }

        /**
         * Sets the largest file {@link Engine#upload} accepts.
         *
         * @param maxBytes the limit in bytes; 25 MB by default
         * @return this builder
         */
        public Builder maxFileBytes(long maxBytes) {
            this.maxFileBytes = maxBytes;
            return this;
        }

        /**
         * Sets who is told about saved changes.
         *
         * @param events the publisher; {@link RecordEvents#none()} by default
         * @return this builder
         */
        public Builder events(RecordEvents events) {
            this.events = events;
            return this;
        }

        /**
         * Registers Java code that actions can call by name with a {@code call} step. Handlers are fixed when the
         * engine is built; an action naming an unknown handler is refused when it is defined.
         *
         * @param name    the name actions use, e.g. {@code billing.refund}; registering a name again replaces it
         * @param handler the code
         * @return this builder
         */
        public Builder handler(String name, ActionHandler handler) {
            handlers.put(Objects.requireNonNull(name), Objects.requireNonNull(handler));
            return this;
        }

        /**
         * Sets the clock for {@code now} in expressions and {@link ActionContext#now()}, e.g. a fixed clock in tests.
         *
         * @param clock the clock; the system UTC clock by default
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Makes the engine.
         *
         * @return a ready engine; models, actions and records already in the repositories are live
         */
        public Engine build() {
            if (models == null) {
                models = ModelService.inMemory();
            }
            return new Engine(this);
        }
    }
}
