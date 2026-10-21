package io.joshuasalcedo.schema.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import io.joshuasalcedo.schema.file.FileRef;

/**
 * Outbound port: where file bytes live. Records hold only a {@link FileRef}; the bytes are put here first and
 * opened from here later. The library ships {@link io.joshuasalcedo.schema.adapter.InMemoryFileStore} and
 * {@link io.joshuasalcedo.schema.adapter.DirectoryFileStore}; a service plugs in a database or object storage (S3, R2).
 * <p>
 * Stores are content-addressed: the location comes from the SHA-256 of the bytes, so the same file uploaded twice is
 * kept once, and a location can never point to different bytes than it did.
 */
public interface FileStore {

    /**
     * Stores bytes and describes them.
     *
     * @param name        the file name to record, e.g. {@code contract.pdf}
     * @param contentType the media type; blank to guess from the name
     * @param content     the bytes; read to the end, not closed
     * @return the file as records hold it
     * @throws UncheckedIOException if the bytes cannot be read or stored
     */
    FileRef put(String name, String contentType, InputStream content);

    /**
     * Opens stored bytes.
     *
     * @param location a {@link FileRef#location()}
     * @return the bytes, for the caller to close; empty when nothing is stored there
     */
    Optional<InputStream> open(String location);

    /**
     * Whether bytes are stored at a location.
     *
     * @param location a {@link FileRef#location()}
     * @return true when they are
     */
    boolean exists(String location);

    /**
     * Removes stored bytes; does nothing when there are none. Records still pointing at them will fail the next
     * check, so delete only what no record uses.
     *
     * @param location a {@link FileRef#location()}
     */
    void delete(String location);

    // --- helpers for implementations -----------------------------------------------------------------------

    /**
     * The content-addressed location of bytes with a checksum.
     *
     * @param sha256 the lower-case hex SHA-256
     * @return {@code sha256/<first two>/<all>}
     */
    static String locationOf(String sha256) {
        return "sha256/" + sha256.substring(0, 2) + "/" + sha256;
    }

    /**
     * The media type to record: the one given, else a guess from the name, else {@code application/octet-stream}.
     *
     * @param name        the file name
     * @param contentType the type given, possibly blank
     * @return the type, lower-case
     */
    static String contentType(String name, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.strip().toLowerCase(java.util.Locale.ROOT);
        }
        String guessed = URLConnection.guessContentTypeFromName(name == null ? "" : name);
        if (guessed == null && name != null) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            guessed = lower.endsWith(".svg") ? "image/svg+xml" : lower.endsWith(".webp") ? "image/webp"
                    : lower.endsWith(".mp4") ? "video/mp4" : lower.endsWith(".webm") ? "video/webm"
                    : lower.endsWith(".mp3") ? "audio/mpeg" : lower.endsWith(".pdf") ? "application/pdf"
                    : lower.endsWith(".json") ? "application/json" : null;
        }
        return guessed == null ? "application/octet-stream" : guessed;
    }

    /**
     * Reads all bytes while computing their SHA-256.
     *
     * @param content the bytes
     * @return the bytes and their checksum
     */
    static Digested digest(InputStream content) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] bytes;
            try (DigestInputStream in = new DigestInputStream(content, sha)) {
                bytes = in.readAllBytes();
            }
            return new Digested(bytes, HexFormat.of().formatHex(sha.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Bytes and their checksum.
     *
     * @param bytes  the bytes
     * @param sha256 their lower-case hex SHA-256
     */
    record Digested(byte[] bytes, String sha256) {
    }
}
