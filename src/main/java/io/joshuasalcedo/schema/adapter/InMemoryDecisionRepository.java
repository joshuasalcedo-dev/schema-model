package io.joshuasalcedo.schema.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.action.DecisionTable;
import io.joshuasalcedo.schema.port.out.DecisionRepository;

/** Keeps decision tables in memory, in the order they were first saved. Thread-safe. For tests, tools and prototypes. */
public final class InMemoryDecisionRepository implements DecisionRepository {

    private final Map<String, DecisionTable> values = new LinkedHashMap<>();

    /** Creates an empty repository. */
    public InMemoryDecisionRepository() {
    }

    @Override
    public synchronized List<DecisionTable> findAll() {
        return new ArrayList<>(values.values());
    }

    @Override
    public synchronized Optional<DecisionTable> findById(String id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public synchronized void save(DecisionTable value) {
        values.put(value.id(), value);
    }

    @Override
    public synchronized void delete(String id) {
        values.remove(id);
    }
}
