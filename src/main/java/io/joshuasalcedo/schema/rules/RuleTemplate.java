package io.joshuasalcedo.schema.rules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Invariant;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.action.Expressions;
import io.joshuasalcedo.schema.text.Labels;

/**
 * A common kind of model rule, such as "the end is not before the start", that becomes an {@link Invariant} once its
 * {@link #parameters()} are filled in with fields of a model (and values), so nobody has to write CEL by hand.
 * <pre>{@code
 * Invariant rule = RuleTemplates.byId("not-before").orElseThrow()
 *         .create(model, Map.of("field", "endDate", "other", "startDate"));
 * // value.endDate >= value.startDate   (or guarded with has() when the dates are optional)
 * }</pre>
 * The CEL it writes only checks values that are there: a comparison of two optional fields holds while either is
 * absent ({@code !has(value.endDate) || ...}). Rules whose point is presence (at least one of, required when, ...)
 * test presence instead; a repeated field counts as present when it holds at least one value. The same arguments
 * always give the same text, which is how {@link RuleTemplates#recognize} finds the template behind a rule.
 * <p>
 * The catalog is in {@link RuleTemplates}; a template is immutable and thread-safe.
 */
public final class RuleTemplate {

    private final String id;
    private final String name;
    private final String description;
    private final List<RuleParameter> parameters;
    private final Generator generator;

    RuleTemplate(String id, String name, String description, List<RuleParameter> parameters, Generator generator) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parameters = List.copyOf(parameters);
        this.generator = generator;
    }

    /**
     * The stable ID, e.g. {@code not-before}.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * The name for people, e.g. "Not before".
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * What the rule means, in one sentence.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * The blanks to fill in, in display order.
     *
     * @return the parameters
     */
    public List<RuleParameter> parameters() {
        return parameters;
    }

    /**
     * Looks up a parameter by name.
     *
     * @param name the parameter name
     * @return the parameter, or empty when the template has none by that name
     */
    public Optional<RuleParameter> parameter(String name) {
        return parameters.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    /**
     * The fields of a model a FIELD or FIELDS parameter may take, on their own; a {@link RuleParameter#typeOf()}
     * tie narrows the choice further once the other field is picked.
     *
     * @param model     the model
     * @param parameter the parameter name
     * @return the acceptable fields in model order; empty for VALUE or unknown parameters
     */
    public List<Field> candidates(Model model, String parameter) {
        return parameter(parameter)
                .map(p -> model.fields().stream().filter(p::accepts).toList())
                .orElse(List.of());
    }

    /**
     * Whether the model has fields to fill every parameter, e.g. two dates of the same type for "not before".
     *
     * @param model the model
     * @return true when the template can be used on the model
     */
    public boolean applicable(Model model) {
        return assign(model, 0, new LinkedHashMap<>());
    }

    private boolean assign(Model model, int index, Map<String, Field> chosen) {
        if (index == parameters.size()) {
            for (RuleParameter p : parameters) {
                if (p.kind() == RuleParameter.Kind.FIELDS
                    && model.fields().stream().filter(f -> p.accepts(f) && !chosen.containsValue(f)).count() < 2) {
                    return false;
                }
            }
            return true;
        }
        RuleParameter p = parameters.get(index);
        if (p.kind() != RuleParameter.Kind.FIELD) {
            return assign(model, index + 1, chosen);
        }
        for (Field field : model.fields()) {
            Field tie = p.typeOf().isEmpty() ? null : chosen.get(p.typeOf());
            if (p.accepts(field) && !chosen.containsValue(field) && (tie == null || sameType(tie, field))) {
                chosen.put(p.name(), field);
                if (assign(model, index + 1, chosen)) {
                    return true;
                }
                chosen.remove(p.name());
            }
        }
        return false;
    }

    /**
     * Fills in the template: checks the arguments against the model and writes the rule's CEL and a message for
     * people that uses the field labels.
     *
     * @param model     the model the rule is for
     * @param arguments by parameter name: a field name for FIELD, field names separated by commas for FIELDS
     *                  ({@code "a, b, c"}), the literal as text for VALUE (a choice value, {@code true}, {@code 42},
     *                  {@code 9.5}, ...)
     * @return the rule
     * @throws SchemaException ({@link SchemaException.Reason#INVALID}) listing every argument that is missing,
     *                         unknown, names a field the model lacks or of a type the parameter does not take, or
     *                         does not parse as the field's type
     */
    public Invariant create(Model model, Map<String, String> arguments) {
        Filled filled = resolve(model, arguments);
        Rule rule = generator.generate(filled, f -> !f.required());
        Optional<String> error = Expressions.shared().check(rule.expression(), true);
        if (error.isPresent()) {
            throw new SchemaException(SchemaException.Reason.INVALID, "arguments",
                                      "arguments give a rule that does not compile: " + error.get());
        }
        return new Invariant(rule.expression(), rule.message());
    }

    /** The expression for the arguments with the given guard policy, or null when the arguments are invalid. */
    String expression(Model model, Map<String, String> arguments, Predicate<Field> guarded) {
        try {
            return generator.generate(resolve(model, arguments), guarded).expression();
        } catch (SchemaException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return id;
    }

    // ---- argument checking ----------------------------------------------------------------------------------

    private Filled resolve(Model model, Map<String, String> arguments) {
        Map<String, String> args = arguments == null ? Map.of() : arguments;
        List<SchemaProblem> problems = new ArrayList<>();
        for (String key : args.keySet()) {
            if (parameter(key).isEmpty()) {
                problem(problems, key, "is not a parameter of " + name);
            }
        }
        Map<String, Field> fields = new LinkedHashMap<>();
        Map<String, List<Field>> lists = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (RuleParameter p : parameters) {
            String raw = args.get(p.name());
            if (raw == null || raw.isBlank()) {
                problem(problems, p.name(), "is required");
                continue;
            }
            switch (p.kind()) {
                case FIELD -> {
                    Field field = field(model, p, raw.strip(), problems);
                    if (field == null) {
                        continue;
                    }
                    Field tie = p.typeOf().isEmpty() ? null : fields.get(p.typeOf());
                    if (tie != null && !sameType(tie, field)) {
                        problem(problems, p.name(), "'" + field.name() + "' must have the same type as '"
                                                    + tie.name() + "' (" + tie.type() + ")");
                    } else if (!used.add(field.name())) {
                        problem(problems, p.name(), "'" + field.name() + "' is already used by another parameter");
                    } else {
                        fields.put(p.name(), field);
                    }
                }
                case FIELDS -> {
                    List<Field> list = new ArrayList<>();
                    boolean ok = true;
                    for (String part : raw.split(",")) {
                        if (part.isBlank()) {
                            continue;
                        }
                        Field field = field(model, p, part.strip(), problems);
                        if (field == null) {
                            ok = false;
                        } else if (!used.add(field.name())) {
                            problem(problems, p.name(), "'" + field.name() + "' is named more than once");
                            ok = false;
                        } else {
                            list.add(field);
                        }
                    }
                    if (ok && list.size() < 2) {
                        problem(problems, p.name(), "needs at least two fields");
                    } else if (ok) {
                        lists.put(p.name(), List.copyOf(list));
                    }
                }
                case VALUE -> { /* checked below, once the fields it depends on are known */ }
            }
        }
        Map<String, Value> values = new LinkedHashMap<>();
        for (RuleParameter p : parameters) {
            String raw = args.get(p.name());
            if (p.kind() != RuleParameter.Kind.VALUE || raw == null || raw.isBlank()) {
                continue;
            }
            if (p.typeOf().isEmpty()) {
                values.put(p.name(), new Value(raw, null, null));
                continue;
            }
            Field typed = fields.get(p.typeOf());
            if (typed == null) {
                continue; // the field it depends on already has a problem
            }
            try {
                values.put(p.name(), literal(typed, raw));
            } catch (IllegalArgumentException e) {
                problem(problems, p.name(), e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
        Filled filled = new Filled(model, fields, lists, values);
        List<SchemaProblem> late = new ArrayList<>();
        try {
            filled.validate(generator);
        } catch (BadArgument e) {
            problem(late, e.parameter, e.getMessage());
        }
        if (!late.isEmpty()) {
            throw new SchemaException(late);
        }
        return filled;
    }

    private Field field(Model model, RuleParameter p, String name, List<SchemaProblem> problems) {
        Optional<Field> found = model.field(name);
        if (found.isEmpty()) {
            problem(problems, p.name(), "'" + name + "' is not a field of " + model.displayName());
            return null;
        }
        Field field = found.get();
        if (!p.accepts(field)) {
            problem(problems, p.name(), "'" + name + "' must be "
                                        + (field.repeated() ? "a single (not repeated) field of type " : "of type ")
                                        + p.types().stream().map(Enum::name).sorted().toList());
            return null;
        }
        return field;
    }

    private static void problem(List<SchemaProblem> problems, String parameter, String message) {
        String path = "arguments." + parameter;
        problems.add(new SchemaProblem(path, path + " " + message));
    }

    private static boolean sameType(Field a, Field b) {
        return a.type() == b.type() && (a.type() != FieldType.MODEL || a.model().equals(b.model()));
    }

    // ---- CEL text ---------------------------------------------------------------------------------------------

    /**
     * A value typed by the user, as CEL and as text for people.
     *
     * @param raw     what the user typed
     * @param cel     the CEL literal; null when the template interprets {@code raw} itself
     * @param display the value in a message; null when the template interprets {@code raw} itself
     */
    record Value(String raw, String cel, String display) {
    }

    /** The CEL literal for a value of a field's type, checked the way a record value of that field is. */
    static Value literal(Field field, String raw) {
        String text = raw.strip();
        return switch (field.type()) {
            case TEXT -> new Value(raw, quote(raw), raw);
            case CHOICE -> {
                if (!field.choices().contains(text)) {
                    throw new IllegalArgumentException("'" + text + "' is not one of " + field.choices());
                }
                yield new Value(raw, quote(text), text);
            }
            case BOOLEAN -> {
                String lower = text.toLowerCase(Locale.ROOT);
                if (!lower.equals("true") && !lower.equals("false")) {
                    throw new IllegalArgumentException("'" + text + "' must be true or false");
                }
                yield new Value(raw, lower, lower);
            }
            case INTEGER -> {
                try {
                    String n = Long.toString(Long.parseLong(text));
                    yield new Value(raw, n, n);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + text + "' is not a whole number");
                }
            }
            case DECIMAL -> {
                String d = doubleLiteral(text);
                yield new Value(raw, d, plain(new BigDecimal(text)));
            }
            case DATE -> {
                try {
                    String date = LocalDate.parse(text).toString();
                    yield new Value(raw, quote(date), date);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("'" + text + "' is not a date such as 2026-10-19");
                }
            }
            case TIMESTAMP -> {
                try {
                    Instant instant = Instant.parse(text);
                    yield new Value(raw, quote(Expressions.timestamp(instant)), instant.toString());
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("'" + text + "' is not an instant such as "
                                                       + "2026-10-19T08:30:00Z");
                }
            }
            case MODEL -> throw new IllegalArgumentException("values of model fields cannot be typed in");
        };
    }

    /** A CEL double literal, e.g. {@code 5.0} or {@code -0.25}. */
    static String doubleLiteral(String text) {
        try {
            String plain = plain(new BigDecimal(text.strip()));
            return plain.contains(".") ? plain : plain + ".0";
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text.strip() + "' is not a number");
        }
    }

    private static String plain(BigDecimal d) {
        return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
    }

    /** A CEL single-quoted string literal. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('\'');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\'' -> out.append("\\'");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('\'').toString();
    }

    /** A field's label inside a sentence: {@code firstName} becomes "first name". */
    static String label(Field field) {
        return Labels.words(field.name()).toLowerCase(Locale.ROOT);
    }

    /** Joins labels for people: "a", "a and b", "a, b and c". */
    static String join(List<Field> fields, String last) {
        List<String> labels = fields.stream().map(RuleTemplate::label).toList();
        if (labels.size() == 1) {
            return labels.getFirst();
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + " " + last + " " + labels.getLast();
    }

    /** Capitalises the first letter of a message. */
    static String sentence(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** The CEL and message a template writes. */
    record Rule(String expression, String message) {
    }

    /** Writes a template's rule from checked arguments. */
    @FunctionalInterface
    interface Generator {
        /**
         * Writes the rule.
         *
         * @param filled  the checked arguments
         * @param guarded which fields get a {@code has()} guard in comparisons
         * @return the CEL and message
         */
        Rule generate(Filled filled, Predicate<Field> guarded);
    }

    /** An argument the template itself refuses (e.g. a limit that is not a number). */
    static final class BadArgument extends RuntimeException {
        private final String parameter;

        BadArgument(String parameter, String message) {
            super(message, null, false, false);
            this.parameter = parameter;
        }
    }

    /** Checked arguments, with the helpers templates write CEL with. */
    static final class Filled {
        private final Model model;
        private final Map<String, Field> fields;
        private final Map<String, List<Field>> lists;
        private final Map<String, Value> values;

        Filled(Model model, Map<String, Field> fields, Map<String, List<Field>> lists, Map<String, Value> values) {
            this.model = model;
            this.fields = fields;
            this.lists = lists;
            this.values = values;
        }

        /** Runs the generator once so template-specific argument checks (BadArgument) happen at resolve time. */
        void validate(Generator generator) {
            generator.generate(this, f -> true);
        }

        Model model() {
            return model;
        }

        Field field(String parameter) {
            return Objects.requireNonNull(fields.get(parameter), parameter);
        }

        List<Field> fields(String parameter) {
            return Objects.requireNonNull(lists.get(parameter), parameter);
        }

        Value value(String parameter) {
            return Objects.requireNonNull(values.get(parameter), parameter);
        }

        /** {@code value.name}. */
        static String ref(Field field) {
            return "value." + field.name();
        }

        /** True when the field has a value; a repeated field needs at least one. */
        static String present(Field field) {
            return field.repeated()
                    ? "(has(value." + field.name() + ") && size(value." + field.name() + ") > 0)"
                    : "has(value." + field.name() + ")";
        }

        /** Prefixes {@code !has(value.x) || } for each guarded field, so the body only runs when they are there. */
        static String guard(Predicate<Field> guarded, String body, Field... compared) {
            StringBuilder out = new StringBuilder();
            for (Field field : compared) {
                if (guarded.test(field)) {
                    out.append('!').append(present(field)).append(" || ");
                }
            }
            return out.append(body).toString();
        }
    }
}
