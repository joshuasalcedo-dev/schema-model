package io.joshuasalcedo.schema.record;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One stored instance of an entity model, e.g. one customer. Records are made and changed only through the
 * platform, which checks them against their model first, so a stored record always fits its model as it was when
 * the record was saved.
 * <p>
 * Values are keyed by field name; a field without a value has no key. Each field type has one Java type:
 * <table>
 *   <caption>Value types</caption>
 *   <tr><th scope="col">Field type</th><th scope="col">Java type</th></tr>
 *   <tr><td>TEXT, CHOICE</td><td>{@code String}</td></tr>
 *   <tr><td>INTEGER</td><td>{@code Long}</td></tr>
 *   <tr><td>DECIMAL</td><td>{@code BigDecimal}</td></tr>
 *   <tr><td>BOOLEAN</td><td>{@code Boolean}</td></tr>
 *   <tr><td>DATE</td><td>{@code LocalDate}</td></tr>
 *   <tr><td>TIMESTAMP</td><td>{@code Instant}</td></tr>
 *   <tr><td>MODEL, a value object</td><td>{@code Map<String, Object>} of its values</td></tr>
 *   <tr><td>MODEL, an entity</td><td>{@code String}: the referenced record's ID</td></tr>
 *   <tr><td>any repeated field</td><td>{@code List} of the above</td></tr>
 * </table>
 *
 * @param model   the entity model's ID
 * @param id      unique within the model: the identity field values (joined with {@code :} when there are several),
 *                or a generated UUID for an entity without identity fields; never changes
 * @param values  the field values, in field order; unmodifiable
 * @param version 1 when created, one more on each change; a write based on an older version is refused
 */
public record Record(String model, String id, Map<String, Object> values, long version) {

    /**
     * Copies the values so the record cannot change later.
     *
     * @throws NullPointerException if {@code model} or {@code id} is null
     */
    public Record {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(id, "id");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * The record's key across all models.
     *
     * @return {@code model/id}, e.g. {@code customer/c-123}
     */
    public String key() {
        return model + "/" + id;
    }

    /**
     * One field's value.
     *
     * @param field the field name
     * @return the value, or empty if the field has none
     */
    public Optional<Object> value(String field) {
        return Optional.ofNullable(values.get(field));
    }
}
