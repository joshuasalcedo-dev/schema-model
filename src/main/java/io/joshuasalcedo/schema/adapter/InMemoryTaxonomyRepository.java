package io.joshuasalcedo.schema.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.Taxonomy;
import io.joshuasalcedo.schema.port.out.TaxonomyRepository;

/** Keeps taxonomies in memory, in the order they were first saved. Thread-safe. For tests, tools and prototypes. */
public final class InMemoryTaxonomyRepository implements TaxonomyRepository {

    private final Map<String, Taxonomy> values = new LinkedHashMap<>();

    /** Creates an empty repository. */
    public InMemoryTaxonomyRepository() {
    }

    @Override
    public synchronized List<Taxonomy> findAll() {
        return new ArrayList<>(values.values());
    }

    @Override
    public synchronized Optional<Taxonomy> findById(String id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public synchronized void save(Taxonomy value) {
        values.put(value.id(), value);
    }

    @Override
    public synchronized void delete(String id) {
        values.remove(id);
    }
}
