package io.joshuasalcedo.schema.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import io.joshuasalcedo.schema.file.FileRef;
import io.joshuasalcedo.schema.port.out.FileStore;

/**
 * Keeps file bytes in a folder on disk, content-addressed ({@code <root>/sha256/3f/3fa9...}). Writes go to a
 * temporary file first and are moved into place, so a half-written file is never visible. For desktop apps and
 * single-machine services.
 */
public final class DirectoryFileStore implements FileStore {

    private final Path root;

    /**
     * Uses a folder, creating it when needed.
     *
     * @param root the folder
     */
    public DirectoryFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public FileRef put(String name, String contentType, InputStream content) {
        FileStore.Digested d = FileStore.digest(content);
        String location = FileStore.locationOf(d.sha256());
        Path target = path(location);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
                Files.write(temp, d.bytes());
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new FileRef(name, FileStore.contentType(name, contentType), d.bytes().length, d.sha256(), location);
    }

    @Override
    public Optional<InputStream> open(String location) {
        Path file = path(location);
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.newInputStream(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(String location) {
        return Files.isRegularFile(path(location));
    }

    @Override
    public void delete(String location) {
        try {
            Files.deleteIfExists(path(location));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The file of a location; refuses locations that would leave the root. */
    private Path path(String location) {
        Path file = root.resolve(location).normalize();
        if (!file.startsWith(root) || !location.matches("sha256/[0-9a-f]{2}/[0-9a-f]{64}")) {
            throw new IllegalArgumentException("not a location of this store: " + location);
        }
        return file;
    }
}
