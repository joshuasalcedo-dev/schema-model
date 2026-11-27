package io.joshuasalcedo.schema.predefined;

import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Ids;
import io.joshuasalcedo.schema.Normalize;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.port.in.ModelDesign;
import io.joshuasalcedo.valueobject.ValueObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The predefined value objects: one {@link Model} for each Java value object of
 * <a href="https://central.sonatype.com/artifact/io.joshuasalcedo/value-object">io.joshuasalcedo:value-object</a>
 * (an email, a phone number, an amount of money, an address...), made from the Java classes themselves so they can't
 * drift apart. Use them from any entity by their ID:
 * <pre>{@code
 * ValueObjectLibrary.install(engine.models());     // adds the ones that are missing
 *
 * engine.models().createModel(Model.entity("Customer")
 *         .field(Field.text("id").identity().required())
 *         .field(Field.model("email", "email").required())
 *         .field(Field.model("phone", "phone-number"))
 *         .build());
 * }</pre>
 *
 * <h2>Made from the Java classes</h2>
 * Each value-object record becomes a model named after its class ({@code PhoneNumber} becomes {@code phone-number}),
 * its components becoming fields: text, whole and exact numbers, true/false, dates and instants map to their field
 * types; a Java enum becomes a {@code CHOICE} of its constant names; a component that is itself a value object
 * becomes a {@code MODEL} field; a list becomes a repeated field. Enums (countries, languages, currencies...) get no
 * model of their own.
 *
 * <h2>Checked and printed by the Java classes</h2>
 * A value for one of these models is checked by its Java value object, so it is valid exactly when Java says so, and
 * stored as Java cleaned it up: given as text it is parsed ({@code "+63 917 123 4567"} becomes the phone number's
 * parts), given as an object it goes through the record's constructor ({@link #parse}). It prints the way the Java
 * value object prints itself ({@link #text}).
 */
public final class ValueObjectLibrary {

    /**
     * Value objects that are not business values (security tokens, file and table helpers): no model is made for
     * them.
     */
    public static final Set<String> EXCLUDED = Set.of("jwt-token", "hashed-password", "table", "pdf-file");

    private static final String METADATA = "value-object-metadata.tsv";

    private static final Map<String, Class<?>> CLASSES = new LinkedHashMap<>();
    private static final Map<String, Model> MODELS = new LinkedHashMap<>();
    private static final Map<Class<?>, ValueObject.Type> TYPES = new LinkedHashMap<>();
    /** model -> field ("" for the model itself) -> key -> value, from {@value #METADATA}. */
    private static final Map<String, Map<String, Map<String, String>>> META = new LinkedHashMap<>();
    /** model -> its rules (expression, message), from {@value #METADATA}. */
    private static final Map<String, List<String[]>> RULES = new LinkedHashMap<>();

    static {
        loadMetadata();
        for (ValueObject.Type type : ValueObject.Type.values()) {
            TYPES.putIfAbsent(type.type(), type);
        }
        for (ValueObject.Type type : ValueObject.Type.values()) {
            add(type.type(), new LinkedHashSet<>());
        }
    }

    private ValueObjectLibrary() {
    }

    // ------------------------------------------------------------------ the models

    /** Every model, each after the models it holds. */
    public static List<Model> all() {
        return List.copyOf(MODELS.values());
    }

    /** The model with this ID, if it is one of the library's. */
    public static Optional<Model> model(String id) {
        return Optional.ofNullable(MODELS.get(id));
    }

    /** The Java class behind a model, if it is one of the library's. */
    public static Optional<Class<?>> javaClass(String id) {
        return Optional.ofNullable(CLASSES.get(id));
    }

    /** Whether {@code model} is the library's model (not one of the same ID made by hand). */
    public static boolean isLibraryModel(Model model) {
        return model != null && model.equals(MODELS.get(model.id()));
    }

    /**
     * Adds the value objects to a schema being built, skipping any whose ID the builder already has (that model is
     * kept as it is).
     *
     * @param builder the schema builder
     * @return the same builder
     */
    public static Schema.Builder install(Schema.Builder builder) {
        Schema current = builder.buildUnchecked();
        for (Model model : all()) {
            if (!current.contains(model.id())) {
                builder.model(model);
            }
        }
        return builder;
    }

    /**
     * Creates the value objects that are missing, in dependency order. A model whose ID already exists is left as it
     * is, so running this again changes nothing.
     *
     * @param design where models are created, such as {@code engine.models()}
     * @return the models created
     */
    public static List<Model> install(ModelDesign design) {
        List<Model> created = new ArrayList<>();
        for (Model model : all()) {
            if (design.getModel(model.id()).isEmpty()) {
                created.add(design.createModel(model));
            }
        }
        return List.copyOf(created);
    }

    // ------------------------------------------------------------------ checking and printing

    /**
     * Checks a value with its Java value object and returns it as Java cleaned it up, in record types.
     *
     * @param id  the model's ID
     * @param raw the value as text (parsed) or as an object of field values (through the record's constructor)
     * @return its fields, cleaned up
     * @throws IllegalArgumentException with Java's reason when the value isn't valid
     */
    public static Map<String, Object> parse(String id, Object raw) {
        Class<?> type = CLASSES.get(id);
        if (type == null) {
            throw new IllegalArgumentException("'" + id + "' is not a value object of the library");
        }
        Object value = construct(type, raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) decompose(value);
        return fields;
    }

    /**
     * The value as its Java value object prints it, e.g. {@code PHP 1,234.50}; null when the fields aren't valid.
     *
     * @param id     the model's ID
     * @param values its fields, in record types
     */
    public static String text(String id, Map<String, ?> values) {
        Class<?> type = CLASSES.get(id);
        if (type == null) {
            return null;
        }
        try {
            return String.valueOf(construct(type, values));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ making the models

    private static void add(Class<?> type, Set<Class<?>> visiting) {
        if (!type.isRecord() || CLASSES.containsValue(type) || !visiting.add(type)) {
            return;
        }
        // the models it holds first
        for (RecordComponent component : type.getRecordComponents()) {
            Class<?> held = elementClass(component.getGenericType(), component.getType());
            if (held.isRecord() && ValueObject.class.isAssignableFrom(held)) {
                add(held, visiting);
            }
        }
        String id = Ids.fromDisplayName(type.getSimpleName());
        if (EXCLUDED.contains(id) || CLASSES.containsKey(id)) {
            return; // not a business value, or two classes with one simple name (the first one keeps the ID)
        }
        Map<String, Map<String, String>> meta = META.getOrDefault(id, Map.of());
        Model.Builder builder = Model.valueObject(type.getSimpleName()).description(description(type, meta));
        for (RecordComponent component : type.getRecordComponents()) {
            Field.Builder field = field(component);
            apply(field, meta.getOrDefault(component.getName(), Map.of()));
            builder.field(field.build());
        }
        for (String[] rule : RULES.getOrDefault(id, List.of())) {
            builder.invariant(rule[0], rule[1]);
        }
        Model model = builder.build();
        CLASSES.put(model.id(), type);
        MODELS.put(model.id(), model);
    }

    private static Field.Builder field(RecordComponent component) {
        Class<?> raw = component.getType();
        boolean repeated = Collection.class.isAssignableFrom(raw);
        Class<?> type = elementClass(component.getGenericType(), raw);
        Field.Builder field;
        List<String> constants = constants(type);
        if (!constants.isEmpty()) {
            field = Field.choice(component.getName(), constants.toArray(String[]::new));
        } else if (type.isRecord() && ValueObject.class.isAssignableFrom(type)) {
            field = Field.model(component.getName(), Model.valueObject(type.getSimpleName()).build().id());
        } else if (type == boolean.class || type == Boolean.class) {
            field = Field.bool(component.getName());
        } else if (isWhole(type)) {
            field = Field.integer(component.getName());
        } else if (isExact(type)) {
            field = Field.decimal(component.getName());
        } else if (type == LocalDate.class) {
            field = Field.date(component.getName());
        } else if (type == Instant.class) {
            field = Field.timestamp(component.getName());
        } else {
            field = Field.text(component.getName());
        }
        if (repeated) {
            field.repeated();
        }
        if (required(component)) {
            field.required();
        }
        return field;
    }

    /**
     * The constants a component can hold: an enum's, or for an interface (such as {@code Currency}) the constants of
     * every enum of the library that implements it; empty for anything else.
     */
    private static List<String> constants(Class<?> type) {
        List<Class<?>> enums = type.isEnum() ? List.of(type)
                : type.isInterface()
                        ? TYPES.keySet().stream().filter(c -> c.isEnum() && type.isAssignableFrom(c)).toList()
                        : List.of();
        List<String> names = new ArrayList<>();
        for (Class<?> e : enums) {
            for (Object constant : e.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Whether the Java value object rejects a missing value: a primitive, or (in a {@code @NullMarked} package or
     * class, as the library's are) a component not marked {@code @Nullable}. Lists and Optionals never are.
     */
    private static boolean required(RecordComponent component) {
        Class<?> raw = component.getType();
        if (raw.isPrimitive()) {
            return true;
        }
        if (raw == Optional.class || Collection.class.isAssignableFrom(raw)) {
            return false;
        }
        boolean nullable = has(component.getAnnotatedType(), "Nullable") || has(component, "Nullable");
        Class<?> owner = component.getDeclaringRecord();
        boolean marked = has(owner, "NullMarked") || (owner.getPackage() != null && has(owner.getPackage(), "NullMarked"));
        return marked && !nullable;
    }

    /** Whether an annotation of this simple name is present (jspecify's, or another library's of that name). */
    private static boolean has(AnnotatedElement element, String simpleName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ descriptions, limits and rules

    private static String description(Class<?> type, Map<String, Map<String, String>> meta) {
        String carried = meta.getOrDefault("", Map.of()).get("description");
        String source = "Checked and printed by the Java value object " + type.getName()
                + " (io.joshuasalcedo:value-object).";
        ValueObject.Type known = TYPES.get(type);
        String shape = known != null && known.detectable() && known.shape() != null && !known.shape().isBlank()
                ? " As text it looks like: " + known.shape()
                : "";
        return (carried == null ? "" : carried + " ") + source + shape;
    }

    private static void apply(Field.Builder field, Map<String, String> meta) {
        meta.forEach((key, value) -> {
            switch (key) {
                case "description" -> field.description(value);
                case "pattern" -> field.pattern(value);
                case "min" -> field.min(value);
                case "max" -> field.max(value);
                case "minLength" -> field.minLength(Integer.parseInt(value));
                case "maxLength" -> field.maxLength(Integer.parseInt(value));
                case "scale" -> field.scale(Integer.parseInt(value));
                case "normalize" -> field.normalize(Arrays.stream(value.split(","))
                        .filter(step -> !step.isBlank())
                        .map(Normalize::valueOf)
                        .toArray(Normalize[]::new));
                default -> throw new IllegalStateException(METADATA + ": unknown key " + key);
            }
        });
    }

    /** Reads {@value #METADATA}: kind, model, field, key, value separated by tabs, a line each; # starts a comment. */
    private static void loadMetadata() {
        try (InputStream in = ValueObjectLibrary.class.getResourceAsStream(METADATA)) {
            if (in == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\t", 5);
                switch (p[0]) {
                    case "model" -> META.computeIfAbsent(p[1], k -> new LinkedHashMap<>())
                            .computeIfAbsent("", k -> new LinkedHashMap<>()).put(p[3], p[4]);
                    case "field" -> META.computeIfAbsent(p[1], k -> new LinkedHashMap<>())
                            .computeIfAbsent(p[2], k -> new LinkedHashMap<>()).put(p[3], p[4]);
                    case "invariant" -> RULES.computeIfAbsent(p[1], k -> new ArrayList<>())
                            .add(new String[]{p[3], p[4]});
                    default -> throw new IllegalStateException(METADATA + ": unknown kind " + p[0]);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Class<?> elementClass(Type generic, Class<?> raw) {
        if ((Collection.class.isAssignableFrom(raw) || raw == Optional.class) && generic instanceof ParameterizedType p
                && p.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        return raw;
    }

    private static boolean isWhole(Class<?> type) {
        return type == int.class || type == long.class || type == short.class || type == byte.class
                || type == Integer.class || type == Long.class || type == Short.class || type == Byte.class
                || type == BigInteger.class;
    }

    private static boolean isExact(Class<?> type) {
        return type == double.class || type == float.class || type == Double.class || type == Float.class
                || type == BigDecimal.class;
    }

    // ------------------------------------------------------------------ Java value <-> record values

    /** The value in record types: records as maps, enums as names, numbers as Long or BigDecimal. */
    static Object decompose(Object value) {
        return switch (value) {
            case null -> null;
            case Enum<?> e -> e.name();
            case Record r -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                for (RecordComponent component : r.getClass().getRecordComponents()) {
                    Object part = decompose(invoke(component.getAccessor(), r));
                    if (part != null) {
                        fields.put(component.getName(), part);
                    }
                }
                yield fields;
            }
            case Optional<?> o -> decompose(o.orElse(null));
            case Collection<?> c -> c.stream().map(ValueObjectLibrary::decompose).toList();
            case Integer i -> i.longValue();
            case Short s -> s.longValue();
            case Byte b -> b.longValue();
            case BigInteger i -> i.longValueExact();
            case Double d -> BigDecimal.valueOf(d);
            case Float f -> new BigDecimal(f.toString());
            case Long l -> l;
            case BigDecimal d -> d;
            case Boolean b -> b;
            case String s -> s;
            case LocalDate d -> d;
            case Instant i -> i;
            default -> value.toString();
        };
    }

    /** The Java value of {@code type} from text (parsed) or a map of field values (the canonical constructor). */
    static Object construct(Class<?> type, Object raw) {
        if (raw == null || type.isInstance(raw)) {
            return raw;
        }
        if (raw instanceof Map<?, ?> map && type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                args[i] = coerce(components[i].getGenericType(), types[i], map.get(components[i].getName()),
                        components[i].getName());
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(types);
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (InvocationTargetException e) {
                throw invalid(type, e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("can't make a " + type.getSimpleName() + ": " + e.getMessage(), e);
            }
        }
        ValueObject.Type known = TYPES.get(type);
        if (known != null) {
            try {
                return known.parse(raw);
            } catch (RuntimeException e) {
                throw invalid(type, e);
            }
        }
        return fromText(type, String.valueOf(raw));
    }

    private static Object coerce(Type generic, Class<?> type, Object raw, String name) {
        if (raw == null) {
            if (type == Optional.class) {
                return Optional.empty();
            }
            if (type.isPrimitive()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return null;
        }
        if (type == Optional.class) {
            return Optional.ofNullable(coerce(null, elementClass(generic, type), raw, name));
        }
        if (Collection.class.isAssignableFrom(type)) {
            if (!(raw instanceof Collection<?> items)) {
                throw new IllegalArgumentException(name + " must be a list");
            }
            Class<?> element = elementClass(generic, type);
            List<Object> list = new ArrayList<>();
            for (Object item : items) {
                list.add(coerce(null, element, item, name));
            }
            return Set.class.isAssignableFrom(type) ? new LinkedHashSet<>(list) : list;
        }
        if (type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class) {
            return primitive(type, raw, name);
        }
        if (type.isEnum()) {
            return constant(type, raw);
        }
        if (type == String.class) {
            return String.valueOf(raw);
        }
        if (type.isInstance(raw)) {
            return raw;
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(String.valueOf(raw));
        }
        if (type == Instant.class) {
            return Instant.parse(String.valueOf(raw));
        }
        if (type.isRecord() || TYPES.containsKey(type)) {
            return construct(type, raw);
        }
        return fromText(type, String.valueOf(raw));
    }

    private static Object primitive(Class<?> type, Object raw, String name) {
        try {
            BigDecimal number = raw instanceof Number n ? new BigDecimal(n.toString()) : null;
            String text = String.valueOf(raw).strip();
            if (type == boolean.class || type == Boolean.class) {
                return raw instanceof Boolean b ? b : Boolean.parseBoolean(text);
            }
            BigDecimal value = number != null ? number : new BigDecimal(text);
            if (type == int.class || type == Integer.class) return value.intValueExact();
            if (type == long.class || type == Long.class) return value.longValueExact();
            if (type == short.class || type == Short.class) return value.shortValueExact();
            if (type == byte.class || type == Byte.class) return value.byteValueExact();
            if (type == double.class || type == Double.class) return value.doubleValue();
            if (type == float.class || type == Float.class) return value.floatValue();
            if (type == BigInteger.class) return value.toBigIntegerExact();
            return value;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(name + " must be a number, not '" + raw + "'");
        }
    }

    private static Object constant(Class<?> type, Object raw) {
        String text = String.valueOf(raw).strip();
        String key = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(key)) {
                return constant;
            }
        }
        ValueObject.Type known = TYPES.get(type);
        if (known != null) {
            Optional<ValueObject> parsed = known.tryParse(text);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not one of " + type.getSimpleName());
    }

    /** Through a static {@code parse}, {@code of} or {@code valueOf} taking text, or a constructor taking text. */
    private static Object fromText(Class<?> type, String text) {
        for (String name : List.of("parse", "of", "valueOf", "from")) {
            for (Class<?> param : List.of(String.class, CharSequence.class, Object.class)) {
                try {
                    Method method = type.getMethod(name, param);
                    if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                        return method.invoke(null, text);
                    }
                } catch (NoSuchMethodException e) {
                    // try the next
                } catch (InvocationTargetException e) {
                    throw invalid(type, e.getCause());
                } catch (IllegalAccessException e) {
                    break;
                }
            }
        }
        try {
            return type.getConstructor(String.class).newInstance(text);
        } catch (InvocationTargetException e) {
            throw invalid(type, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("can't read a " + type.getSimpleName() + " from text", e);
        }
    }

    private static Object invoke(Method accessor, Object target) {
        try {
            accessor.setAccessible(true);
            return accessor.invoke(target);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IllegalArgumentException invalid(Class<?> type, Throwable cause) {
        String reason = cause == null || cause.getMessage() == null ? "is not valid" : cause.getMessage();
        return new IllegalArgumentException(reason, cause);
    }
}
