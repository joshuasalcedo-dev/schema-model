package io.joshuasalcedo.schema.port.out;

import java.util.List;
import java.util.Optional;
import io.joshuasalcedo.schema.record.Record;

/**
 * Outbound port: where records are kept. The library ships {@link io.joshuasalcedo.schema.adapter.InMemoryRecordRepository}.
 * <p>
 * Writes are conditional on the version, so two engines (or two calls) that read the same record cannot both save
 * over it. A database adapter does this with {@code UPDATE ... WHERE version = ?} and a unique key on
 * {@code (model, id)}. Records arrive here already checked; the repository only stores them.
 */
public interface RecordRepository {

    /**
     * All records of a model.
     *
     * @param model the model ID
     * @return the records in a stable order, e.g. creation order; empty for an unknown model
     */
    List<Record> findAll(String model);

    /**
     * Looks up a record.
     *
     * @param model the model ID
     * @param id    the record ID
     * @return the record, or empty if there is none
     */
    Optional<Record> find(String model, String id);

    /**
     * Stores {@code record} if the stored version is {@code record.version() - 1}, where version 1 means "must not
     * exist yet". Stores nothing otherwise.
     *
     * @param record the record to store
     * @return true if it was stored; false if the stored version did not match
     */
    boolean save(Record record);

    /**
     * Removes a record if its stored version is {@code version}.
     *
     * @param model   the model ID
     * @param id      the record ID
     * @param version the version the caller read
     * @return true if it was removed; false if it was missing or its version did not match
     */
    boolean delete(String model, String id, long version);
}
