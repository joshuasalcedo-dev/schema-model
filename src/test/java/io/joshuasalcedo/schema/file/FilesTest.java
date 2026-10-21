package io.joshuasalcedo.schema.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.adapter.DirectoryFileStore;
import io.joshuasalcedo.schema.engine.Engine;
import io.joshuasalcedo.schema.port.out.FileStore;
import io.joshuasalcedo.schema.predefined.ValueObjects;
import io.joshuasalcedo.schema.record.Record;
import io.joshuasalcedo.schema.text.RecordLookup;
import io.joshuasalcedo.schema.text.ValueFormats;

class FilesTest {

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private Engine engine() {
        Engine engine = Engine.builder().maxFileBytes(1024).build();
        ValueObjects.install(engine.models());
        engine.models().createModel(Model.entity("Product")
                .field(Field.text("sku").identity().required())
                .field(Field.model("photo", "image"))
                .field(Field.model("manual", "file"))
                .field(Field.model("gallery", "image").repeated())
                .build());
        return engine;
    }

    @Test
    void uploadsAreContentAddressedAndDescribed() throws IOException {
        Engine engine = engine();
        FileRef a = engine.upload("notes.txt", "", bytes("hello"));
        FileRef b = engine.upload("copy.txt", "", bytes("hello"));

        assertThat(a.sha256()).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(a.location()).isEqualTo(b.location()).isEqualTo("sha256/2c/" + a.sha256());
        assertThat(a.sizeBytes()).isEqualTo(5);
        assertThat(a.contentType()).isEqualTo("text/plain");
        try (InputStream in = engine.download(a.location()).orElseThrow()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
        assertThat(engine.download("sha256/00/" + "0".repeat(64))).isEmpty();
    }

    @Test
    void recordsMayOnlyPointToStoredFiles() {
        Engine engine = engine();
        FileRef png = engine.upload("photo.png", "image/png", bytes("not really a png"));
        FileRef pdf = engine.upload("manual.pdf", "", bytes("%PDF"));

        Record saved = engine.create("product", Map.of(
                "sku", "p-1",
                "photo", Map.of("file", png.toValues(), "width", 800, "height", 600, "altText", " A red mug "),
                "manual", pdf.toValues()));
        assertThat(FileRef.from((Map<?, ?>) saved.values().get("manual"))).contains(pdf);

        Map<String, Object> ghost = new HashMap<>(png.toValues());
        ghost.put("location", FileStore.locationOf("f".repeat(64)));
        ghost.put("sha256", "f".repeat(64));
        assertThatThrownBy(() -> engine.create("product", Map.of("sku", "p-2", "photo", Map.of("file", ghost))))
                .isInstanceOfSatisfying(SchemaException.class, e -> assertThat(e.problems())
                        .extracting(p -> p.path()).containsExactly("record.photo.file"));
        assertThatThrownBy(() -> engine.create("product", Map.of(
                "sku", "p-3", "gallery", java.util.List.of(Map.of("file", png.toValues()), Map.of("file", ghost)))))
                .hasMessageContaining("record.gallery[1].file");
        // an image field refuses a file that is not an image
        assertThatThrownBy(() -> engine.create("product", Map.of("sku", "p-4", "photo", Map.of("file", pdf.toValues()))))
                .hasMessageContaining("the file is not an image");
    }

    @Test
    void uploadsAreLimitedInSize() {
        Engine engine = engine();
        assertThatThrownBy(() -> engine.upload("big.bin", "", new ByteArrayInputStream(new byte[2048])))
                .isInstanceOf(SchemaException.class).hasMessageContaining("larger than 1024 bytes");
    }

    @Test
    void directoryStoreKeepsFilesOnDisk(@TempDir Path dir) throws IOException {
        DirectoryFileStore store = new DirectoryFileStore(dir);
        FileRef ref = store.put("a.txt", "", bytes("abc"));
        assertThat(dir.resolve(ref.location())).exists();
        assertThat(store.exists(ref.location())).isTrue();
        try (InputStream in = store.open(ref.location()).orElseThrow()) {
            assertThat(in.readAllBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
        }
        assertThatThrownBy(() -> store.open("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        store.delete(ref.location());
        assertThat(store.exists(ref.location())).isFalse();
    }

    @Test
    void mediaShowAsText() {
        Engine engine = engine();
        ValueFormats formats = ValueFormats.defaults(engine.models().schema(), RecordLookup.none(), Locale.US,
                                                     java.time.ZoneOffset.UTC);
        Map<String, Object> file = new FileRef("report.pdf", "application/pdf", 1_258_291, "a".repeat(64),
                                               "x").toValues();
        assertThat(formats.text("file", file)).isEqualTo("report.pdf (1.2 MB)");
        assertThat(formats.text("image", Map.of("file", Map.of("name", "photo.jpg"), "width", 1920L,
                                                "height", 1080L))).isEqualTo("photo.jpg · 1920×1080");
        assertThat(formats.text("video", Map.of("file", Map.of("name", "clip.mp4"),
                                                "durationSeconds", new java.math.BigDecimal("83.4"))))
                .isEqualTo("clip.mp4 · 1:23");
    }
}
