package io.joshuasalcedo.schema.text;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.predefined.ValueObjectFormats;
import io.joshuasalcedo.schema.record.Record;

/**
 * How values look as text: in tables, lists, tooltips and read-only views. Every field type has a default, and every
 * value object can have its own format, keyed by model ID; the predefined value objects come with theirs.
 * <pre>{@code
 * ValueFormats formats = ValueFormats.defaults(schema, RecordLookup.of(engine));
 * formats.text(customer.field("balance").orElseThrow(), new BigDecimal("1234.5"));   // "1,234.50"
 * formats.text("money", Map.of("amount", 149.99, "currency", Map.of("code", "PHP"))); // "PHP 149.99"
 * formats.register("loyalty-tier", (values, f) -> "Tier " + values.get("level"));
 * }</pre>
 * Formats never throw: a value that does not fit is shown as plain text.
 */
public final class ValueFormats {

    /**
     * Turns the values of one value object into text.
     */
    @FunctionalInterface
    public interface ObjectFormat {
        /**
         * Formats one value object.
         *
         * @param values  the object's values by field name, in record types (see {@link Record}); absent fields have
         *                no key
         * @param formats the formats, to show nested values
         * @return the text
         */
        String format(Map<String, ?> values, ValueFormats formats);
    }

    private final Schema schema;
    private final RecordLookup lookup;
    private final Locale locale;
    private final ZoneId zone;
    private final Map<String, ObjectFormat> objects = new ConcurrentHashMap<>();

    private ValueFormats(Schema schema, RecordLookup lookup, Locale locale, ZoneId zone) {
        this.schema = Objects.requireNonNull(schema);
        this.lookup = Objects.requireNonNull(lookup);
        this.locale = Objects.requireNonNull(locale);
        this.zone = Objects.requireNonNull(zone);
    }

    /**
     * Formats with every default and the predefined value object formats, in the default locale and time zone.
     *
     * @param schema the models, to resolve fields that hold other models
     * @param lookup the records, to show entity references by their text
     * @return the formats
     */
    public static ValueFormats defaults(Schema schema, RecordLookup lookup) {
        return defaults(schema, lookup, Locale.getDefault(), ZoneId.systemDefault());
    }

    /**
     * Formats with every default and the predefined value object formats.
     *
     * @param schema the models
     * @param lookup the records
     * @param locale for numbers and dates
     * @param zone   for timestamps
     * @return the formats
     */
    public static ValueFormats defaults(Schema schema, RecordLookup lookup, Locale locale, ZoneId zone) {
        ValueFormats formats = new ValueFormats(schema, lookup, locale, zone);
        ValueObjectFormats.register(formats);
        return formats;
    }

    /**
     * Sets the format of a value object, replacing any default.
     *
     * @param modelId the value object's model ID
     * @param format  the format
     * @return these formats
     */
    public ValueFormats register(String modelId, ObjectFormat format) {
        objects.put(modelId, format);
        return this;
    }

    /**
     * The schema these formats resolve models in.
     *
     * @return the schema
     */
    public Schema schema() {
        return schema;
    }

    // --- formatting ----------------------------------------------------------------------------------------

    /**
     * One field's value as text; a repeated field's values are joined with commas.
     *
     * @param field the field
     * @param value its value in record types, or null
     * @return the text; empty for no value
     */
    public String text(Field field, Object value) {
        if (value == null) {
            return "";
        }
        if (field.repeated() && value instanceof Collection<?> items) {
            return items.stream().map(v -> single(field, v)).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
        }
        return single(field, value);
    }

    /**
     * A value object's values as text, in its registered format (or "field: value" pairs when it has none).
     *
     * @param modelId the value object's model ID
     * @param values  its values
     * @return the text
     */
    public String text(String modelId, Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        ObjectFormat format = objects.get(modelId);
        if (format != null) {
            try {
                return format.format(values, this);
            } catch (RuntimeException e) {
                // a value that does not fit its format is still shown
            }
        }
        Model model = schema.model(modelId).orElse(null);
        if (model == null) {
            return values.toString();
        }
        List<String> parts = new ArrayList<>();
        for (Field field : model.fields()) {
            String text = text(field, values.get(field.name()));
            if (!text.isEmpty()) {
                parts.add(model.fields().size() == 1 ? text : Labels.of(field) + ": " + text);
            }
        }
        return String.join(", ", parts);
    }

    /**
     * A record as one line of text, for pickers and references: its identity or its first text field, then its ID.
     *
     * @param record the record
     * @return the text
     */
    public String text(Record record) {
        Model model = schema.model(record.model()).orElse(null);
        if (model != null) {
            for (Field field : model.fields()) {
                if (!field.identity() && field.type() == FieldType.TEXT && record.values().get(field.name()) != null) {
                    return record.values().get(field.name()) + " (" + record.id() + ")";
                }
            }
        }
        return record.id();
    }

    /**
     * A number with grouping, e.g. 1,234.5; whole numbers without decimals.
     *
     * @param number the number
     * @return the text
     */
    public String number(Number number) {
        if (number instanceof BigDecimal d) {
            DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(locale);
            format.setMinimumFractionDigits(Math.max(0, d.scale()));
            format.setMaximumFractionDigits(Math.max(0, d.scale()));
            format.setParseBigDecimal(true);
            return format.format(d);
        }
        return NumberFormat.getIntegerInstance(locale).format(number);
    }

    /**
     * A date in the locale's medium style, e.g. Oct 18, 2026.
     *
     * @param date the date
     * @return the text
     */
    public String date(LocalDate date) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date);
    }

    /**
     * An instant in the time zone, medium style, e.g. Oct 18, 2026, 2:00:00 PM.
     *
     * @param instant the instant
     * @return the text
     */
    public String timestamp(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
                .format(instant);
    }

    private String single(Field field, Object value) {
        try {
            return switch (field.type()) {
                case TEXT -> String.valueOf(value);
                case CHOICE -> Labels.choice(String.valueOf(value));
                case BOOLEAN -> Boolean.TRUE.equals(value) ? "Yes" : "No";
                case INTEGER, DECIMAL -> value instanceof Number n ? number(n) : String.valueOf(value);
                case DATE -> value instanceof LocalDate d ? date(d) : date(LocalDate.parse(String.valueOf(value)));
                case TIMESTAMP -> value instanceof Instant i ? timestamp(i)
                        : timestamp(Instant.parse(String.valueOf(value)));
                case MODEL -> model(field, value);
            };
        } catch (RuntimeException e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private String model(Field field, Object value) {
        Model target = schema.model(field.model()).orElse(null);
        if (value instanceof Map<?, ?> map) {
            return text(field.model(), (Map<String, ?>) map);
        }
        if (target != null && target.isEntity()) {
            String id = String.valueOf(value);
            return lookup.record(target.id(), id).map(this::text).orElse(id);
        }
        return String.valueOf(value);
    }
}
