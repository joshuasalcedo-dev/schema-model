package io.joshuasalcedo.schema.predefined;

import static io.joshuasalcedo.schema.Normalize.BLANK_TO_ABSENT;
import static io.joshuasalcedo.schema.Normalize.LOWERCASE;
import static io.joshuasalcedo.schema.Normalize.TRIM;

import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.port.in.ModelDesign;
import io.joshuasalcedo.schema.text.ValueFormats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The platform's own value objects for files and media ({@code file}, {@code image}, {@code video}, {@code audio}):
 * not Java value objects but the shape of a {@link io.joshuasalcedo.schema.file.FileRef}, made by uploading. Install
 * them next to {@link ValueObjectLibrary} when records hold files.
 */
public final class MediaObjects {

    /** {@code file}: a stored file — its name, type, size, checksum and where its bytes are. */
    public static final Model FILE = Model.valueObject("File")
            .description("A stored file: what it is and where its bytes are kept; the bytes are in the file store, "
                         + "not in the record. Made by uploading.")
            .field(Field.text("name").required().normalize(TRIM).maxLength(255)
                           .pattern("[^/\\\\\\x00-\\x1F\\x7F]+")
                           .description("The file name as uploaded, e.g. contract.pdf; no slashes."))
            .field(Field.text("contentType").required().normalize(TRIM, LOWERCASE)
                           .pattern("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
                           .description("The media type, e.g. application/pdf or image/png."))
            .field(Field.integer("sizeBytes").required().min("0").description("The size in bytes."))
            .field(Field.text("sha256").required().normalize(TRIM, LOWERCASE).pattern("[0-9a-f]{64}")
                           .description("The SHA-256 of the bytes: the same bytes always have the same checksum."))
            .field(Field.text("location").required()
                           .description("Where the file store keeps the bytes; set by the store, never by hand."))
            .build();

    /** {@code image}: a stored picture with its size and a text alternative. */
    public static final Model IMAGE = Model.valueObject("Image")
            .description("A picture: a stored image file, its size in pixels and a description for people who "
                         + "cannot see it.")
            .field(Field.model("file", "file").required())
            .field(Field.integer("width").min("1").description("In pixels."))
            .field(Field.integer("height").min("1").description("In pixels."))
            .field(Field.text("altText").normalize(TRIM, BLANK_TO_ABSENT).maxLength(500)
                           .description("What the picture shows, for screen readers and when it cannot load."))
            .invariant("value.file.contentType.startsWith('image/')", "the file is not an image")
            .build();

    /** {@code video}: a stored video with its size and length. */
    public static final Model VIDEO = Model.valueObject("Video")
            .description("A video: a stored video file, its frame size and its length.")
            .field(Field.model("file", "file").required())
            .field(Field.integer("width").min("1").description("In pixels."))
            .field(Field.integer("height").min("1").description("In pixels."))
            .field(Field.decimal("durationSeconds").min("0").scale(3).description("The length in seconds."))
            .invariant("value.file.contentType.startsWith('video/')", "the file is not a video")
            .build();

    /** {@code audio}: a stored sound recording with its length. */
    public static final Model AUDIO = Model.valueObject("Audio")
            .description("A sound recording: a stored audio file and its length.")
            .field(Field.model("file", "file").required())
            .field(Field.decimal("durationSeconds").min("0").scale(3).description("The length in seconds."))
            .invariant("value.file.contentType.startsWith('audio/')", "the file is not audio")
            .build();

    private MediaObjects() {
    }

    /** Every media model, {@code file} first (the others hold it). */
    public static List<Model> all() {
        return List.of(FILE, IMAGE, VIDEO, AUDIO);
    }

    /** Adds the media models a schema being built lacks. */
    public static Schema.Builder install(Schema.Builder builder) {
        Schema current = builder.buildUnchecked();
        for (Model model : all()) {
            if (!current.contains(model.id())) {
                builder.model(model);
            }
        }
        return builder;
    }

    /** Creates the media models that are missing; running it again changes nothing. */
    public static List<Model> install(ModelDesign design) {
        List<Model> created = new ArrayList<>();
        for (Model model : all()) {
            if (design.getModel(model.id()).isEmpty()) {
                created.add(design.createModel(model));
            }
        }
        return List.copyOf(created);
    }

    // ------------------------------------------------------------------ as text

    /**
     * Registers how the media models look as text: {@code report.pdf (1.2 MB)}, {@code photo.jpg · 1920×1080},
     * {@code clip.mp4 · 1920×1080 · 1:05}. Called by {@link ValueFormats#defaults}.
     *
     * @param formats the formats to add to
     */
    public static void register(ValueFormats formats) {
        formats.register("file", (v, f) -> s(v, "name") + (v.get("sizeBytes") instanceof Number n
                ? " (" + bytes(n.longValue()) + ")" : ""));
        formats.register("image", (v, f) -> join(" · ", s(nested(v, "file"), "name"), dimensions(v)));
        formats.register("video", (v, f) -> join(" · ", s(nested(v, "file"), "name"), dimensions(v),
                                                  length(v.get("durationSeconds"))));
        formats.register("audio", (v, f) -> join(" · ", s(nested(v, "file"), "name"),
                                                  length(v.get("durationSeconds"))));
    }

    /**
     * A size in bytes for people, in powers of 1024: {@code 512 B}, {@code 1.2 MB}.
     *
     * @param bytes the size
     * @return the text
     */
    public static String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int i = -1;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return BigDecimal.valueOf(value).setScale(value < 10 ? 1 : 0, RoundingMode.HALF_UP).stripTrailingZeros()
                       .toPlainString() + " " + units[i];
    }

    /**
     * A length in seconds as {@code m:ss} or {@code h:mm:ss}.
     *
     * @param seconds the length; null gives empty
     * @return the text
     */
    public static String length(Object seconds) {
        if (!(seconds instanceof Number n)) {
            return "";
        }
        long total = Math.round(n.doubleValue());
        return total >= 3600 ? String.format("%d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
                : String.format("%d:%02d", total / 60, total % 60);
    }

    private static String dimensions(Map<String, ?> values) {
        return values.get("width") instanceof Number w && values.get("height") instanceof Number h
                ? w.longValue() + "×" + h.longValue() : "";
    }

    private static String s(Map<String, ?> values, String key) {
        Object v = values == null ? null : values.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> nested(Map<String, ?> values, String key) {
        return values.get(key) instanceof Map<?, ?> m ? (Map<String, ?>) m : Map.of();
    }

    private static String join(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part);
            }
        }
        return String.join(separator, kept);
    }
}
