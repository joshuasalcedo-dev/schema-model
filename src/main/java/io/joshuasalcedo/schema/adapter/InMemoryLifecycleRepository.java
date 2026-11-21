package io.joshuasalcedo.schema.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.Lifecycle;
import io.joshuasalcedo.schema.port.out.LifecycleRepository;

/** Keeps lifecycles in memory, in the order they were first saved. Thread-safe. For tests, tools and prototypes. */
public final class InMemoryLifecycleRepository implements LifecycleRepository {

    private final Map<String, Lifecycle> values = new LinkedHashMap<>();

    /** Creates an empty repository. */
    public InMemoryLifecycleRepository() {
    }

    @Override
    public synchronized List<Lifecycle> findAll() {
        return new ArrayList<>(values.values());
    }

    @Override
    public synchronized Optional<Lifecycle> findById(String id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public synchronized void save(Lifecycle value) {
        values.put(value.id(), value);
    }

    @Override
    public synchronized void delete(String id) {
        values.remove(id);
    }
}
