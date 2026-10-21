package io.joshuasalcedo.schema.record;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Invariant;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Normalize;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.action.Expressions;

/**
 * Checks values against a model and converts them to the record types (see {@link Record}), the way a Java value
 * object's constructor would. For each field, in order:
 * <ol>
 *   <li>clean up text with the field's {@link Field#normalize()} steps (a blank optional value may become absent);</li>
 *   <li>require a value when the field is required;</li>
 *   <li>convert to the record type, accepting what JSON, CEL and Java callers naturally pass: any whole number for
 *       INTEGER, numbers or numeric strings for DECIMAL, ISO-8601 strings for DATE and TIMESTAMP;</li>
 *   <li>round DECIMAL values to the field's {@code scale};</li>
 *   <li>check the pattern and length of text, the choices, and the {@code min}/{@code max} of numbers.</li>
 * </ol>
 * Then, when all fields of an object passed, the model's {@link Model#invariants()} are checked. Embedded value
 * objects go through the same steps, so a {@code DateRange} inside an entity keeps its own rules.
 */
public final class RecordValues {

    private static final int MAX_CHOICES_IN_MESSAGE = 12;

    private RecordValues() {
    }

    /**
     * The outcome of {@link #convert}: the converted values, or the problems that stop them being stored.
     *
     * @param values     the values that could be converted, in field order; only safe to store when
     *                   {@link #valid()}
     * @param problems   every reason the values cannot be stored; empty when they can
     * @param references every entity reference found (also inside value objects and lists), as (model ID, record
     *                   ID), for the caller to check that those records exist
     */
    public record Result(Map<String, Object> values, List<SchemaProblem> problems,
                         List<Map.Entry<String, String>> references) {

        /**
         * Whether the values fit the model.
         *
         * @return true when there are no problems
         */
        public boolean valid() {
            return problems.isEmpty();
        }
    }

    /**
     * Checks and converts values, with invariants evaluated at the current time.
     *
     * @param model  the model the values are for
     * @param schema the schema, to resolve fields that hold other models
     * @param input  the values by field name; a null value counts as absent
     * @param at     the path prefix for problems, e.g. {@code record} or {@code input}
     * @return the converted values, problems and references
     */
    public static Result convert(Model model, Schema schema, Map<String, ?> input, String at) {
        return convert(model, schema, input, at, Instant.now());
    }

    /**
     * Checks {@code input} against {@code model} and converts each value to its record type (see the class
     * description for the steps).
     *
     * @param model  the model the values are for
     * @param schema the schema, to resolve fields that hold other models
     * @param input  the values by field name; a null value counts as absent
     * @param at     the path prefix for problems, e.g. {@code record} or {@code input}
     * @param now    the moment invariants see as {@code now} and {@code today}
     * @return the converted values, problems and references
     */
    public static Result convert(Model model, Schema schema, Map<String, ?> input, String at, Instant now) {
        Context context = new Context(schema, new ArrayList<>(), new ArrayList<>(), Expressions.clock(now));
        Map<String, Object> values = object(model, input, at, context);
        return new Result(values, context.problems(), context.references());
    }

    /** What one conversion shares across nested objects. */
    private record Context(Schema schema, List<SchemaProblem> problems, List<Map.Entry<String, String>> references,
                           Map<String, Object> clock) {
    }

    private static Map<String, Object> object(Model model, Map<String, ?> input, String at, Context context) {
        int problemsBefore = context.problems().size();
        for (String key : input.keySet()) {
            if (model.field(key).isEmpty()) {
                add(context, at + "." + key, "is not a field of " + model.displayName());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field field : model.fields()) {
            String path = at + "." + field.name();
            Object raw = input.get(field.name());
            if (field.repeated() && raw != null) {
                if (!(raw instanceof Collection<?> items)) {
                    add(context, path, "must be a list");
                    continue;
                }
                List<Object> list = new ArrayList<>();
                int i = 0;
                for (Object item : items) {
                    Object cleaned = clean(field, item);
                    if (cleaned == null) {
                        if (item == null) {
                            add(context, path + "[" + i + "]", "is null");
                        }
                    } else {
                        Object v = single(field, cleaned, path + "[" + i + "]", context);
                        if (v != null) {
                            list.add(v);
                        }
                    }
                    i++;
                }
                if (items.isEmpty() && field.required()) {
                    add(context, path, "needs at least one value");
                }
                out.put(field.name(), List.copyOf(list));
                continue;
            }
            Object cleaned = clean(field, raw);
            if (cleaned == null) {
                if (field.required()) {
                    add(context, path, "is required");
                }
                continue;
            }
            Object v = single(field, cleaned, path, context);
            if (v != null) {
                out.put(field.name(), v);
            }
        }
        if (context.problems().size() == problemsBefore) {
            invariants(model, out, at, context);
        }
        return out;
    }

    /** The value after the field's clean-up steps; null when it is (or became) absent. */
    private static Object clean(Field field, Object raw) {
        if (raw instanceof String s && !field.normalize().isEmpty()
            && (field.type() == FieldType.TEXT || field.type() == FieldType.CHOICE)) {
            return Normalize.applyAll(field.normalize(), s);
        }
        return raw;
    }

    /** One value of {@code field}; null (with a problem added) when it cannot be converted or breaks a rule. */
    private static Object single(Field field, Object raw, String at, Context context) {
        switch (field.type()) {
            case TEXT -> {
                if (!(raw instanceof String s)) {
                    add(context, at, "must be text");
                    return null;
                }
                int length = s.codePointCount(0, s.length());
                if (field.minLength() != null && length < field.minLength()) {
                    add(context, at, "must have at least " + field.minLength() + " characters");
                    return null;
                }
                if (field.maxLength() != null && length > field.maxLength()) {
                    add(context, at, "must have at most " + field.maxLength() + " characters");
                    return null;
                }
                if (!field.pattern().isEmpty() && !Pattern.compile(field.pattern()).matcher(s).matches()) {
                    add(context, at, "'" + s + "' does not match " + field.pattern());
                    return null;
                }
                return s;
            }
            case CHOICE -> {
                if (!(raw instanceof String s) || !field.choices().contains(s)) {
                    add(context, at, (raw instanceof String s ? "'" + s + "' is not allowed; it " : "")
                                     + "must be one of " + choices(field));
                    return null;
                }
                return s;
            }
            case BOOLEAN -> {
                if (!(raw instanceof Boolean b)) {
                    add(context, at, "must be true or false");
                    return null;
                }
                return b;
            }
            case INTEGER -> {
                Long l = toLong(raw);
                if (l == null) {
                    add(context, at, "must be a whole number");
                    return null;
                }
                return inRange(field, BigDecimal.valueOf(l), at, context) ? l : null;
            }
            case DECIMAL -> {
                BigDecimal d = toDecimal(raw);
                if (d == null) {
                    add(context, at, "must be a number");
                    return null;
                }
                if (field.scale() != null) {
                    d = d.setScale(field.scale(), RoundingMode.HALF_EVEN);
                }
                return inRange(field, d, at, context) ? d : null;
            }
            case DATE -> {
                if (raw instanceof LocalDate d) {
                    return d;
                }
                try {
                    return LocalDate.parse(String.valueOf(raw).strip());
                } catch (DateTimeParseException e) {
                    add(context, at, "must be a date like 2026-10-18");
                    return null;
                }
            }
            case TIMESTAMP -> {
                if (raw instanceof Instant i) {
                    return i;
                }
                if (raw instanceof OffsetDateTime o) {
                    return o.toInstant();
                }
                String text = String.valueOf(raw).strip();
                try {
                    return Instant.parse(text);
                } catch (DateTimeParseException e) {
                    try {
                        return OffsetDateTime.parse(text).toInstant();
                    } catch (DateTimeParseException e2) {
                        add(context, at, "must be a timestamp like 2026-10-18T14:00:00Z");
                        return null;
                    }
                }
            }
            case MODEL -> {
                Model target = context.schema().model(field.model()).orElse(null);
                if (target == null) {
                    add(context, at, "model '" + field.model() + "' was not found");
                    return null;
                }
                if (target.isEntity()) {
                    // entities are referenced by their record ID
                    if (!(raw instanceof String id) || id.isBlank()) {
                        add(context, at, "must be the ID of a " + target.displayName());
                        return null;
                    }
                    context.references().add(Map.entry(target.id(), id));
                    return id;
                }
                if (!(raw instanceof Map<?, ?> map)) {
                    add(context, at, "must be a " + target.displayName() + " object");
                    return null;
                }
                Map<String, Object> nested = new LinkedHashMap<>();
                map.forEach((k, v) -> nested.put(String.valueOf(k), v));
                return object(target, nested, at, context);
            }
        }
        return null;
    }

    private static boolean inRange(Field field, BigDecimal value, String at, Context context) {
        if (field.min() != null && value.compareTo(field.min()) < 0) {
            add(context, at, value.toPlainString() + " is below the minimum " + field.min().toPlainString());
            return false;
        }
        if (field.max() != null && value.compareTo(field.max()) > 0) {
            add(context, at, value.toPlainString() + " is above the maximum " + field.max().toPlainString());
            return false;
        }
        return true;
    }

    private static void invariants(Model model, Map<String, Object> values, String at, Context context) {
        if (model.invariants().isEmpty()) {
            return;
        }
        Map<String, Object> variables = new HashMap<>(context.clock());
        variables.put("value", toExpressionValue(values));
        for (Invariant invariant : model.invariants()) {
            Object result;
            try {
                result = Expressions.shared().eval(invariant.expression(), variables);
            } catch (IllegalArgumentException e) {
                add(context, at, "could not check '" + invariant.expression() + "': " + e.getMessage());
                continue;
            }
            if (!Boolean.TRUE.equals(result)) {
                context.problems().add(new SchemaProblem(at, at + ": " + invariant.message()));
            }
        }
    }

    private static String choices(Field field) {
        List<String> all = field.choices();
        if (all.size() <= MAX_CHOICES_IN_MESSAGE) {
            return all.toString();
        }
        return all.subList(0, MAX_CHOICES_IN_MESSAGE) + " and " + (all.size() - MAX_CHOICES_IN_MESSAGE) + " more";
    }

    private static Long toLong(Object raw) {
        return switch (raw) {
            case Long l -> l;
            case Integer i -> i.longValue();
            case Short s -> s.longValue();
            case Byte b -> b.longValue();
            case BigInteger b when b.bitLength() < 64 -> b.longValue();
            case Number n -> {
                BigDecimal d = toDecimal(n);
                try {
                    yield d == null ? null : d.longValueExact();
                } catch (ArithmeticException e) {
                    yield null;
                }
            }
            case String s -> {
                try {
                    yield Long.parseLong(s.strip());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private static BigDecimal toDecimal(Object raw) {
        try {
            return switch (raw) {
                case BigDecimal d -> d;
                case BigInteger b -> new BigDecimal(b);
                case Double d when d.isNaN() || d.isInfinite() -> null;
                case Float f when f.isNaN() || f.isInfinite() -> null;
                case Double d -> BigDecimal.valueOf(d);
                case Float f -> new BigDecimal(f.toString());
                case Number n -> BigDecimal.valueOf(n.longValue());
                case String s -> new BigDecimal(s.strip());
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A record value as CEL expressions see it: {@code BigDecimal} becomes a double, {@code LocalDate} ISO-8601 text
     * and {@code Instant} fixed-width ISO-8601 text ({@link Expressions#timestamp}), both of which compare correctly
     * as text; maps and lists are converted inside; everything else is already a CEL type. Null entries in maps are
     * dropped, so {@code has()} is false for them.
     *
     * @param value a record value, or null
     * @return the CEL value, or null for null
     */
    public static Object toExpressionValue(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal d -> d.doubleValue();
            case LocalDate d -> d.toString();
            case Instant i -> Expressions.timestamp(i);
            case Map<?, ?> m -> {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, v) -> {
                    if (v != null) {
                        out.put(String.valueOf(k), toExpressionValue(v));
                    }
                });
                yield out;
            }
            case Collection<?> c -> c.stream().map(RecordValues::toExpressionValue).toList();
            default -> value;
        };
    }

    /**
     * The record ID made from the identity values: their text joined with {@code :}, in field order.
     *
     * @param model  the entity model
     * @param values the record's converted values
     * @return the ID, or null when the model has no identity fields or one of them has no value
     */
    public static String identity(Model model, Map<String, Object> values) {
        List<Field> identity = model.identityFields();
        if (identity.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Field field : identity) {
            Object v = values.get(field.name());
            if (v == null) {
                return null;
            }
            parts.add(String.valueOf(v));
        }
        return String.join(":", parts);
    }

    private static void add(Context context, String path, String message) {
        context.problems().add(new SchemaProblem(path, path + " " + message));
    }
}
