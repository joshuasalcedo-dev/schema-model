package io.joshuasalcedo.schema.file;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a record keeps about a stored file: never the bytes, only where they are and what they are. These are the
 * values of the predefined {@code file} value object, so a field {@code Field.model("contract", "file")} holds one.
 * The bytes live in a {@link io.joshuasalcedo.schema.port.out.FileStore}.
 *
 * @param name        the file name as uploaded, e.g. {@code contract.pdf}
 * @param contentType the media type, e.g. {@code application/pdf}, {@code image/png}
 * @param sizeBytes   the size
 * @param sha256      the SHA-256 of the bytes, lower-case hex: the same bytes always have the same checksum
 * @param location    where the store keeps the bytes, e.g. {@code sha256/3f/3fa9...}; opaque to everything but the
 *                    store
 */
public record FileRef(String name, String contentType, long sizeBytes, String sha256, String location) {

    /** Checks that every part is present. */
    public FileRef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(location, "location");
    }

    /**
     * The file as the {@code file} value object's values, ready to put in a record.
     *
     * @return {@code name}, {@code contentType}, {@code sizeBytes}, {@code sha256}, {@code location}
     */
    public Map<String, Object> toValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("contentType", contentType);
        values.put("sizeBytes", sizeBytes);
        values.put("sha256", sha256);
        values.put("location", location);
        return values;
    }

    /**
     * Reads a {@code file} value object's values.
     *
     * @param values the values, e.g. from a record
     * @return the file, or empty when a part is missing
     */
    public static Optional<FileRef> from(Map<?, ?> values) {
        if (values == null || !(values.get("name") instanceof String name)
            || !(values.get("contentType") instanceof String type) || !(values.get("sha256") instanceof String sha)
            || !(values.get("location") instanceof String location)
            || !(values.get("sizeBytes") instanceof Number size)) {
            return Optional.empty();
        }
        return Optional.of(new FileRef(name, type, size.longValue(), sha, location));
    }

    /**
     * Whether the media type starts with {@code prefix}, e.g. {@code image/}.
     *
     * @param prefix the type prefix
     * @return true when it does
     */
    public boolean is(String prefix) {
        return contentType.startsWith(prefix);
    }
}
