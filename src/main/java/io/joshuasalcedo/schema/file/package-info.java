/**
 * Files and media: records hold a {@link io.joshuasalcedo.schema.file.FileRef} (name, type, size, checksum, location) in a
 * predefined {@code file}, {@code image}, {@code video} or {@code audio} value object, and the bytes live in a
 * {@link io.joshuasalcedo.schema.port.out.FileStore}. Upload with {@link io.joshuasalcedo.schema.port.in.Platform#upload}, put the result
 * in a record, and the engine checks on every save that the bytes it points to are stored.
 */
package io.joshuasalcedo.schema.file;
