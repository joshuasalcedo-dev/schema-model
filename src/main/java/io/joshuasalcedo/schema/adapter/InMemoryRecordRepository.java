package io.joshuasalcedo.schema.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.joshuasalcedo.schema.port.out.RecordRepository;
import io.joshuasalcedo.schema.record.Record;

/** Keeps records in memory, in creation order. Thread-safe. For tests, tools and prototypes. */
public final class InMemoryRecordRepository implements RecordRepository {

    /** Creates an empty repository. */
    public InMemoryRecordRepository() {
    }

    private final Map<String, Map<String, Record>> byModel = new LinkedHashMap<>();

    @Override
    public synchronized List<Record> findAll(String model) {
        return new ArrayList<>(byModel.getOrDefault(model, Map.of()).values());
    }

    @Override
    public synchronized Optional<Record> find(String model, String id) {
        return Optional.ofNullable(byModel.getOrDefault(model, Map.of()).get(id));
    }

    @Override
    public synchronized boolean save(Record record) {
        Map<String, Record> records = byModel.computeIfAbsent(record.model(), m -> new LinkedHashMap<>());
        Record stored = records.get(record.id());
        long storedVersion = stored == null ? 0 : stored.version();
        if (storedVersion != record.version() - 1) {
            return false;
        }
        records.put(record.id(), record);
        return true;
    }

    @Override
    public synchronized boolean delete(String model, String id, long version) {
        Map<String, Record> records = byModel.getOrDefault(model, Map.of());
        Record stored = records.get(id);
        if (stored == null || stored.version() != version) {
            return false;
        }
        records.remove(id);
        return true;
    }
}
