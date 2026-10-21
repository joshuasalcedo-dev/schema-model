package io.joshuasalcedo.schema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import io.joshuasalcedo.schema.action.Expressions;

/**
 * The rules a model must follow: a model needs a display name and a
 * valid ID; field names are valid and unique; terms are in the vocabulary and models exist in the schema; choices
 * need values; only entities have identity fields; patterns are valid and only on text; and value objects cannot
 * embed themselves, directly or through other value objects.
 */
public final class SchemaValidator {

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private SchemaValidator() {
    }

    /**
     * Checks every model of the schema against the rest of it.
     *
     * @param schema the schema
     * @return every problem, with paths like {@code models[customer].fields[1].name}; empty when valid
     */
    public static List<SchemaProblem> validate(Schema schema) {
        List<SchemaProblem> problems = new ArrayList<>();
        for (Model model : schema.models()) {
            check(model, schema, "models[" + model.id() + "]", problems);
        }
        return problems;
    }

    /**
     * Checks {@code model} as if it were added to {@code schema}, replacing any model with the same ID. Use it
     * before saving a new or changed model.
     *
     * @param model  the model to check
     * @param schema the models it may refer to and the vocabulary; null checks the model on its own
     * @return every problem, with paths like {@code model.fields[1].name}; empty when valid
     */
    public static List<SchemaProblem> validate(Model model, Schema schema) {
        List<SchemaProblem> problems = new ArrayList<>();
        check(model, schema, "model", problems);
        return problems;
    }

    /**
     * Checks {@code model} on its own: fields holding other models are only checked for the shape of the ID, any
     * term is accepted, and self-embedding is not looked for.
     *
     * @param model the model to check
     * @return every problem; empty when valid
     */
    public static List<SchemaProblem> validate(Model model) {
        return validate(model, null);
    }

    /**
     * Like {@link #validate(Model, Schema)} but throws instead of returning the problems.
     *
     * @param model  the model to check
     * @param schema the models it may refer to and the vocabulary; null checks the model on its own
     * @throws SchemaException with {@link SchemaException.Reason#INVALID} and every problem, if there are any
     */
    public static void requireValid(Model model, Schema schema) {
        List<SchemaProblem> problems = validate(model, schema);
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
    }

    /**
     * Like {@link #validate(Schema)} but throws instead of returning the problems.
     *
     * @param schema the schema
     * @throws SchemaException with {@link SchemaException.Reason#INVALID} and every problem, if there are any
     */
    public static void requireValid(Schema schema) {
        List<SchemaProblem> problems = validate(schema);
        if (!problems.isEmpty()) {
            throw new SchemaException(problems);
        }
    }

    private static void check(Model model, Schema schema, String at, List<SchemaProblem> problems) {
        Vocabulary vocabulary = schema == null ? Vocabulary.open() : schema.vocabulary();
        if (model.displayName().isBlank()) {
            add(problems, at + ".display_name", "is required");
        }
        if (!Ids.isValid(model.id())) {
            add(problems, at + ".id", "must match " + Ids.ID + " (got '" + model.id() + "')");
        }
        if (model.kind() == null) {
            add(problems, at + ".kind", "is required: ENTITY or VALUE_OBJECT");
        }
        if (!model.term().isEmpty() && !vocabulary.hasTerm(model.term())) {
            add(problems, at + ".term", "'" + model.term() + "' is not in the vocabulary");
        }
        for (int i = 0; i < model.relatedArticles().size(); i++) {
            if (model.relatedArticles().get(i).isBlank()) {
                add(problems, at + ".related_articles[" + i + "]", "is blank");
            }
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < model.fields().size(); i++) {
            checkField(model, model.fields().get(i), schema, vocabulary, at + ".fields[" + i + "]", names, problems);
        }
        for (int i = 0; i < model.invariants().size(); i++) {
            String path = at + ".invariants[" + i + "]";
            Invariant invariant = model.invariants().get(i);
            if (invariant.expression().isBlank()) {
                add(problems, path, "is empty");
            } else {
                Expressions.shared().check(invariant.expression(), true).ifPresent(e -> add(problems, path, e));
            }
        }
        if (schema != null && model.kind() == ModelKind.VALUE_OBJECT) {
            // Value objects are embedded whole: one that (indirectly) embeds itself would never end
            Deque<String> path = new ArrayDeque<>();
            if (embedsItself(schema, model, model, path, new HashSet<>())) {
                add(problems, at, "value objects cannot embed themselves: " + String.join(" → ", path));
            }
        }
    }

    /** Clean-ups only on text, number limits only on numbers, lengths only on text, scale only on decimals. */
    private static void checkLimits(Field field, String at, List<SchemaProblem> problems) {
        FieldType type = field.type();
        boolean text = type == FieldType.TEXT;
        boolean number = type == FieldType.INTEGER || type == FieldType.DECIMAL;
        if (!field.normalize().isEmpty() && !text && type != FieldType.CHOICE) {
            add(problems, at + ".normalize", "is only for TEXT and CHOICE fields");
        }
        if ((field.min() != null || field.max() != null) && !number) {
            add(problems, at + (field.min() != null ? ".min" : ".max"), "is only for INTEGER and DECIMAL fields");
        }
        if (field.min() != null && field.max() != null && field.min().compareTo(field.max()) > 0) {
            add(problems, at + ".min", "is above max");
        }
        if (type == FieldType.INTEGER) {
            for (var limit : java.util.Arrays.asList(field.min(), field.max())) {
                if (limit != null && limit.stripTrailingZeros().scale() > 0) {
                    add(problems, at + (limit == field.min() ? ".min" : ".max"),
                        "must be a whole number for an INTEGER field");
                }
            }
        }
        if ((field.minLength() != null || field.maxLength() != null) && !text) {
            add(problems, at + (field.minLength() != null ? ".minLength" : ".maxLength"), "is only for TEXT fields");
        }
        if (field.minLength() != null && field.minLength() < 0) {
            add(problems, at + ".minLength", "must be 0 or more");
        }
        if (field.maxLength() != null && field.maxLength() < 0) {
            add(problems, at + ".maxLength", "must be 0 or more");
        }
        if (field.minLength() != null && field.maxLength() != null && field.minLength() > field.maxLength()) {
            add(problems, at + ".minLength", "is above maxLength");
        }
        if (field.scale() != null && type != FieldType.DECIMAL) {
            add(problems, at + ".scale", "is only for DECIMAL fields");
        } else if (field.scale() != null && (field.scale() < 0 || field.scale() > 18)) {
            add(problems, at + ".scale", "must be from 0 to 18");
        }
    }

    private static void checkField(Model model, Field field, Schema schema, Vocabulary vocabulary, String at,
                                   Set<String> names, List<SchemaProblem> problems) {
        if (!FIELD_NAME.matcher(field.name()).matches()) {
            add(problems, at + ".name", "must start with a letter and use letters, digits and underscores");
        } else if (!names.add(field.name().toLowerCase(Locale.ROOT))) {
            add(problems, at + ".name", "'" + field.name() + "' is used twice");
        }
        if (!field.term().isEmpty() && !vocabulary.hasTerm(field.term())) {
            add(problems, at + ".term", "'" + field.term() + "' is not in the vocabulary");
        }
        if (field.type() == null) {
            add(problems, at + ".type", "is required");
            return;
        }
        checkLimits(field, at, problems);
        switch (field.type()) {
            case MODEL -> {
                if (field.model().isBlank()) {
                    add(problems, at + ".model", "is required");
                } else if (!Ids.isValid(field.model())) {
                    add(problems, at + ".model", "must match " + Ids.ID + " (got '" + field.model() + "')");
                } else if (schema != null && !field.model().equals(model.id()) && !schema.contains(field.model())) {
                    add(problems, at + ".model", "model '" + field.model() + "' was not found");
                }
            }
            case CHOICE -> {
                if (field.choices().stream().allMatch(String::isBlank)) {
                    add(problems, at + ".choices", "needs at least one value");
                }
            }
            default -> {
            }
        }
        if (field.type() != FieldType.MODEL && !field.model().isEmpty()) {
            add(problems, at + ".model", "is only for MODEL fields");
        }
        if (field.identity() && model.kind() == ModelKind.VALUE_OBJECT) {
            add(problems, at + ".identity", "a value object has no identity; it is defined by all its values");
        }
        if (!field.pattern().isEmpty()) {
            if (field.type() != FieldType.TEXT) {
                add(problems, at + ".pattern", "is only for TEXT fields");
            } else {
                try {
                    Pattern.compile(field.pattern());
                } catch (PatternSyntaxException e) {
                    add(problems, at + ".pattern", "is not a valid regular expression: " + e.getDescription());
                }
            }
        }
    }

    /**
     * True if following value-object fields from {@code current} leads back to {@code validating} (the model being
     * checked, which may differ from the one stored in the schema); {@code path} then holds the way round.
     */
    private static boolean embedsItself(Schema schema, Model validating, Model current, Deque<String> path,
                                        Set<String> seen) {
        path.addLast(current.displayName());
        for (Field field : current.fields()) {
            if (field.type() != FieldType.MODEL) {
                continue;
            }
            boolean backToStart = field.model().equals(validating.id());
            Model target = backToStart ? validating : schema.model(field.model()).orElse(null);
            if (target == null || target.kind() != ModelKind.VALUE_OBJECT) {
                continue; // entities are referenced by identity, not embedded
            }
            if (backToStart) {
                path.addLast(validating.displayName());
                return true;
            }
            if (seen.add(target.id()) && embedsItself(schema, validating, target, path, seen)) {
                return true;
            }
        }
        path.removeLast();
        return false;
    }

    private static void add(List<SchemaProblem> problems, String path, String message) {
        problems.add(new SchemaProblem(path, path + " " + message));
    }
}
