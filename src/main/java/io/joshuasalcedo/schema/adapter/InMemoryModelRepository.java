package io.joshuasalcedo.schema.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.port.out.ModelRepository;

/** Keeps models in memory, in the order they were first saved. Thread-safe. For tests, tools and prototypes. */
public final class InMemoryModelRepository implements ModelRepository {

    /** Creates an empty repository. */
    public InMemoryModelRepository() {
    }

    private final Map<String, Model> models = new LinkedHashMap<>();

    @Override
    public synchronized List<Model> findAll() {
        return new ArrayList<>(models.values());
    }

    @Override
    public synchronized Optional<Model> findById(String id) {
        return Optional.ofNullable(models.get(id));
    }

    @Override
    public synchronized void save(Model model) {
        models.put(model.id(), model);
    }

    @Override
    public synchronized void delete(String id) {
        models.remove(id);
    }
}
