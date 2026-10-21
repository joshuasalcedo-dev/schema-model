package io.joshuasalcedo.schema.adapter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import io.joshuasalcedo.schema.file.FileRef;
import io.joshuasalcedo.schema.port.out.FileStore;

/** Keeps file bytes in memory, content-addressed. Thread-safe. For tests, tools and prototypes. */
public final class InMemoryFileStore implements FileStore {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    /** Creates an empty store. */
    public InMemoryFileStore() {
    }

    @Override
    public FileRef put(String name, String contentType, InputStream content) {
        FileStore.Digested d = FileStore.digest(content);
        String location = FileStore.locationOf(d.sha256());
        files.putIfAbsent(location, d.bytes());
        return new FileRef(name, FileStore.contentType(name, contentType), d.bytes().length, d.sha256(), location);
    }

    @Override
    public Optional<InputStream> open(String location) {
        byte[] bytes = files.get(location);
        return bytes == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(bytes));
    }

    @Override
    public boolean exists(String location) {
        return files.containsKey(location);
    }

    @Override
    public void delete(String location) {
        files.remove(location);
    }
}
