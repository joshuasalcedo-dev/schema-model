package io.joshuasalcedo.schema.text;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.record.Record;

/**
 * Where the UI finds records, for fields that refer to an entity: to offer them in a picker and to show a reference
 * by its record's text instead of a bare ID. Backed by the engine, a REST client or a cache; {@link #none()} when
 * there are no records to offer.
 */
public interface RecordLookup {

    /**
     * The records of a model that a reference field may pick from.
     *
     * @param model the entity model's ID
     * @return the records; empty when there are none or they cannot be listed
     */
    List<Record> records(String model);

    /**
     * One record, to show a reference.
     *
     * @param model the entity model's ID
     * @param id    the record ID
     * @return the record, or empty when it is unknown
     */
    default Optional<Record> record(String model, String id) {
        return records(model).stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /**
     * A lookup with no records: references show as their IDs and pickers offer free text.
     *
     * @return the empty lookup
     */
    static RecordLookup none() {
        return model -> List.of();
    }

    /**
     * A lookup over a platform (e.g. the engine).
     *
     * @param platform the platform
     * @return a lookup reading its records
     */
    static RecordLookup of(io.joshuasalcedo.schema.port.in.Platform platform) {
        return new RecordLookup() {
            @Override
            public List<Record> records(String model) {
                return platform.list(model);
            }

            @Override
            public Optional<Record> record(String model, String id) {
                return platform.get(model, id);
            }
        };
    }
}
