package io.joshuasalcedo.schema.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.action.Action;
import io.joshuasalcedo.schema.port.out.ActionRepository;

/** Keeps action definitions in memory, in the order they were first saved. Thread-safe. */
public final class InMemoryActionRepository implements ActionRepository {

    /** Creates an empty repository. */
    public InMemoryActionRepository() {
    }

    private final Map<String, Action> actions = new LinkedHashMap<>();

    @Override
    public synchronized List<Action> findAll() {
        return new ArrayList<>(actions.values());
    }

    @Override
    public synchronized Optional<Action> find(String name) {
        return Optional.ofNullable(actions.get(name));
    }

    @Override
    public synchronized void save(Action action) {
        actions.put(action.name(), action);
    }

    @Override
    public synchronized void delete(String name) {
        actions.remove(name);
    }
}
