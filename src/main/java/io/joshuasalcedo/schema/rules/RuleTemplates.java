package io.joshuasalcedo.schema.rules;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Invariant;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.rules.RuleParameter.Kind;
import io.joshuasalcedo.schema.rules.RuleTemplate.Filled;
import io.joshuasalcedo.schema.rules.RuleTemplate.Rule;

/**
 * The catalog of {@link RuleTemplate}s: comparisons of dates and numbers, and rules about which fields must be given
 * together. Pick a template, fill in its parameters with fields of the model and get an {@link Invariant}:
 * <pre>{@code
 * for (RuleTemplate template : RuleTemplates.applicable(model)) { ... }   // offer what fits the model
 * Invariant rule = RuleTemplates.byId("required-when").orElseThrow()
 *         .create(model, Map.of("target", "reason", "condition", "status", "value", "closed"));
 * // !(has(value.status) && value.status == 'closed') || has(value.reason)
 * Optional<RuleMatch> again = RuleTemplates.recognize(rule, model);  // the same template and arguments
 * }</pre>
 */
public final class RuleTemplates {

    private static final Set<FieldType> TEMPORAL = EnumSet.of(FieldType.DATE, FieldType.TIMESTAMP);
    private static final Set<FieldType> NUMBER = EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL);
    private static final Set<FieldType> COMPARABLE = EnumSet.complementOf(EnumSet.of(FieldType.MODEL));
    private static final Set<FieldType> CONDITION =
            EnumSet.of(FieldType.CHOICE, FieldType.TEXT, FieldType.BOOLEAN, FieldType.INTEGER);
    private static final Set<FieldType> ANY = Set.of();

    private static final List<RuleTemplate> ALL = List.of(
            compare("not-before", "Not before",
                    "One date or time is the same as or later than another, e.g. the end is not before the start.",
                    TEMPORAL, true, "Date or time", "Not before", ">=", "%s cannot be before %s"),
            compare("after", "After",
                    "One date or time is strictly later than another.",
                    TEMPORAL, true, "Date or time", "After", ">", "%s must be after %s"),
            relativeToNow("not-in-future", "Not in the future",
                          "A date or time is not later than today (or now), e.g. a date of birth.",
                          "<=", "%s cannot be in the future"),
            relativeToNow("not-in-past", "Not in the past",
                          "A date or time is not earlier than today (or now), e.g. a due date.",
                          ">=", "%s cannot be in the past"),
            compare("different", "Different",
                    "Two fields of the same type do not hold the same value.",
                    COMPARABLE, true, "Field", "Different from", "!=", "%s and %s must differ"),
            compare("at-most", "At most",
                    "One number is not greater than another, e.g. the discount is at most the price.",
                    NUMBER, false, "Number", "At most", "<=", "%s cannot be more than %s"),
            compare("less-than", "Less than",
                    "One number is strictly smaller than another.",
                    NUMBER, false, "Number", "Less than", "<", "%s must be less than %s"),
            totalAtMost(),
            presence("at-least-one", "At least one of",
                     "At least one of the fields has a value.",
                     (fields, present) -> String.join(" || ", present),
                     fields -> "at least one of " + RuleTemplate.join(fields, "or") + " is required"),
            presence("only-one", "Only one of",
                     "At most one of the fields has a value.",
                     (fields, present) -> count(present) + " <= 1",
                     fields -> "only one of " + RuleTemplate.join(fields, "or") + " may be given"),
            presence("exactly-one", "Exactly one of",
                     "Exactly one of the fields has a value.",
                     (fields, present) -> count(present) + " == 1",
                     fields -> "exactly one of " + RuleTemplate.join(fields, "or") + " is required"),
            presence("required-together", "Required together",
                     "Either all of the fields have a value or none of them do.",
                     (fields, present) -> "(" + String.join(" && ", present) + ") || ("
                                          + present.stream().map(p -> "!" + p).collect(Collectors.joining(" && "))
                                          + ")",
                     fields -> RuleTemplate.join(fields, "and") + " must be given together or not at all"),
            when("required-when", "Required when",
                 "A field must have a value whenever another field holds a given value.",
                 false, "%s is required when %s is %s"),
            when("forbidden-when", "Forbidden when",
                 "A field must be empty whenever another field holds a given value.",
                 true, "%s must be empty when %s is %s"));

    private static final Map<String, RuleTemplate> BY_ID =
            ALL.stream().collect(Collectors.toUnmodifiableMap(RuleTemplate::id, t -> t));

    /** Guard policies tried by {@link #recognize}: the one {@code create} uses first, then always and never. */
    private static final List<Predicate<Field>> GUARDS = List.of(f -> !f.required(), f -> true, f -> false);
    private static final Pattern FIELD_REF = Pattern.compile("\\bvalue\\.([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern LITERAL = Pattern.compile(
            "'((?:[^'\\\\]|\\\\.)*)'|(?<![\\w.])(-?[0-9]+(?:\\.[0-9]+)?)|\\b(true|false)\\b");

    private RuleTemplates() {
    }

    /**
     * Every template, in display order: date comparisons, number comparisons, then rules about which fields are
     * given.
     *
     * @return the templates
     */
    public static List<RuleTemplate> all() {
        return ALL;
    }

    /**
     * Looks up a template by its ID.
     *
     * @param id e.g. {@code not-before}
     * @return the template, or empty for an unknown ID
     */
    public static Optional<RuleTemplate> byId(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    /**
     * The templates the model has fields for, e.g. no date templates for a model without dates.
     *
     * @param model the model
     * @return the applicable templates, in display order
     */
    public static List<RuleTemplate> applicable(Model model) {
        return ALL.stream().filter(t -> t.applicable(model)).toList();
    }

    /**
     * Finds the template and arguments that generate a rule, so a rule written earlier (by a template or by hand in
     * the same form) can be shown and edited as a template. Only the expression is compared; the message may differ.
     * A comparison is recognised with or without its {@code has()} guards.
     *
     * @param invariant the rule
     * @param model     the model it belongs to
     * @return the match, or empty when no template writes exactly that expression
     */
    public static Optional<RuleMatch> recognize(Invariant invariant, Model model) {
        String target = squeeze(invariant.expression());
        if (target.isEmpty()) {
            return Optional.empty();
        }
        List<String> referenced = new ArrayList<>(new LinkedHashSet<>(
                FIELD_REF.matcher(invariant.expression()).results().map(r -> r.group(1)).toList()));
        if (referenced.isEmpty() || referenced.stream().anyMatch(n -> model.field(n).isEmpty())) {
            return Optional.empty();
        }
        List<String> literals = literals(invariant.expression());
        for (RuleTemplate template : ALL) {
            for (Map<String, String> arguments : candidates(template, referenced, literals)) {
                for (Predicate<Field> guard : GUARDS) {
                    String expression = template.expression(model, arguments, guard);
                    if (expression != null && squeeze(expression).equals(target)) {
                        return Optional.of(new RuleMatch(template, arguments));
                    }
                }
            }
        }
        return Optional.empty();
    }

    // ---- recognising ------------------------------------------------------------------------------------------

    /** Argument sets built from the fields and literals an expression mentions. */
    private static List<Map<String, String>> candidates(RuleTemplate template, List<String> referenced,
                                                        List<String> literals) {
        List<Map<String, String>> out = new ArrayList<>();
        fill(template.parameters(), 0, new LinkedHashMap<>(), new ArrayList<>(referenced), literals, out);
        return out;
    }

    private static void fill(List<RuleParameter> parameters, int index, Map<String, String> chosen,
                             List<String> remaining, List<String> literals, List<Map<String, String>> out) {
        if (index == parameters.size()) {
            if (remaining.isEmpty()) {
                out.add(Map.copyOf(chosen));
            }
            return;
        }
        RuleParameter p = parameters.get(index);
        switch (p.kind()) {
            case FIELD -> {
                for (int i = 0; i < remaining.size(); i++) {
                    List<String> rest = new ArrayList<>(remaining);
                    chosen.put(p.name(), rest.remove(i));
                    fill(parameters, index + 1, chosen, rest, literals, out);
                    chosen.remove(p.name());
                }
            }
            case FIELDS -> {
                if (remaining.size() >= 2) {
                    chosen.put(p.name(), String.join(", ", remaining));
                    fill(parameters, index + 1, chosen, new ArrayList<>(), literals, out);
                    chosen.remove(p.name());
                }
            }
            case VALUE -> {
                for (String literal : literals) {
                    chosen.put(p.name(), literal);
                    fill(parameters, index + 1, chosen, remaining, literals, out);
                    chosen.remove(p.name());
                }
            }
        }
    }

    /** The literals in an expression, unescaped, in order of appearance. */
    private static List<String> literals(String expression) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = LITERAL.matcher(expression);
        while (m.find()) {
            if (m.group(1) != null) {
                out.add(unescape(m.group(1)));
            } else if (m.group(2) != null) {
                out.add(m.group(2));
            } else {
                out.add(m.group(3));
            }
        }
        return List.copyOf(out);
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The expression without white space outside string literals, so spacing does not matter when comparing. */
    private static String squeeze(String expression) {
        StringBuilder out = new StringBuilder(expression.length());
        boolean quoted = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (quoted) {
                out.append(c);
                if (c == '\\' && i + 1 < expression.length()) {
                    out.append(expression.charAt(++i));
                } else if (c == '\'') {
                    quoted = false;
                }
            } else if (c == '\'') {
                quoted = true;
                out.append(c);
            } else if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ---- the templates ----------------------------------------------------------------------------------------

    private static RuleParameter field(String name, String label, Set<FieldType> types, String typeOf) {
        return new RuleParameter(name, label, Kind.FIELD, types, typeOf);
    }

    /** {@code field op other}; numbers of different types are compared as doubles. */
    private static RuleTemplate compare(String id, String name, String description, Set<FieldType> types,
                                        boolean sameType, String fieldLabel, String otherLabel, String op,
                                        String message) {
        return new RuleTemplate(id, name, description,
                                List.of(field("field", fieldLabel, types, ""),
                                        field("other", otherLabel, types, sameType ? "field" : "")),
                                (filled, guarded) -> {
                                    Field a = filled.field("field");
                                    Field b = filled.field("other");
                                    boolean mixed = a.type() != b.type();
                                    String body = number(a, mixed) + " " + op + " " + number(b, mixed);
                                    return new Rule(Filled.guard(guarded, body, a, b),
                                                    RuleTemplate.sentence(message.formatted(RuleTemplate.label(a),
                                                                                            RuleTemplate.label(b))));
                                });
    }

    private static String number(Field field, boolean asDouble) {
        return asDouble && field.type() == FieldType.INTEGER ? "double(" + Filled.ref(field) + ")" : Filled.ref(field);
    }

    /** {@code field op today} for a date, {@code field op now} for a timestamp. */
    private static RuleTemplate relativeToNow(String id, String name, String description, String op, String message) {
        return new RuleTemplate(id, name, description, List.of(field("field", "Date or time", TEMPORAL, "")),
                                (filled, guarded) -> {
                                    Field a = filled.field("field");
                                    String clock = a.type() == FieldType.DATE ? "today" : "now";
                                    return new Rule(Filled.guard(guarded, Filled.ref(a) + " " + op + " " + clock, a),
                                                    RuleTemplate.sentence(message.formatted(RuleTemplate.label(a))));
                                });
    }

    /** The sum of the given numbers (absent ones count as 0) is at most a limit. */
    private static RuleTemplate totalAtMost() {
        return new RuleTemplate(
                "total-at-most", "Total at most",
                "The numbers add up to no more than a limit; absent ones count as zero.",
                List.of(new RuleParameter("fields", "Numbers", Kind.FIELDS, NUMBER, ""),
                        new RuleParameter("limit", "At most", Kind.VALUE, Set.of(), "")),
                (filled, guarded) -> {
                    List<Field> fields = filled.fields("fields");
                    boolean decimal = fields.stream().anyMatch(f -> f.type() == FieldType.DECIMAL);
                    String raw = filled.value("limit").raw().strip();
                    String limit;
                    try {
                        limit = decimal ? RuleTemplate.doubleLiteral(raw) : Long.toString(Long.parseLong(raw));
                    } catch (NumberFormatException e) {
                        throw new RuleTemplate.BadArgument("limit", "'" + raw + "' is not a whole number");
                    } catch (IllegalArgumentException e) {
                        throw new RuleTemplate.BadArgument("limit", e.getMessage());
                    }
                    String zero = decimal ? "0.0" : "0";
                    List<String> terms = new ArrayList<>();
                    for (Field f : fields) {
                        String term = number(f, decimal);
                        terms.add(guarded.test(f) ? "(has(" + Filled.ref(f) + ") ? " + term + " : " + zero + ")"
                                                  : term);
                    }
                    String shown = decimal ? limit.replaceAll("\\.0$", "") : limit;
                    return new Rule(String.join(" + ", terms) + " <= " + limit,
                                    RuleTemplate.sentence("the total of " + RuleTemplate.join(fields, "and")
                                                          + " cannot be more than " + shown));
                });
    }

    /** Writes a presence rule from the {@code present(...)} terms of the fields. */
    @FunctionalInterface
    private interface PresenceBody {
        String write(List<Field> fields, List<String> present);
    }

    /** Writes the message of a presence rule. */
    @FunctionalInterface
    private interface PresenceMessage {
        String write(List<Field> fields);
    }

    private static RuleTemplate presence(String id, String name, String description, PresenceBody body,
                                         PresenceMessage message) {
        return new RuleTemplate(id, name, description,
                                List.of(new RuleParameter("fields", "Fields", Kind.FIELDS, ANY, "")),
                                (filled, guarded) -> {
                                    List<Field> fields = filled.fields("fields");
                                    List<String> present = fields.stream().map(Filled::present).toList();
                                    return new Rule(body.write(fields, present),
                                                    RuleTemplate.sentence(message.write(fields)));
                                });
    }

    private static String count(List<String> present) {
        return present.stream().map(p -> "(" + p + " ? 1 : 0)").collect(Collectors.joining(" + "));
    }

    /** {@code target} must be present (or absent) whenever {@code condition == value}. */
    private static RuleTemplate when(String id, String name, String description, boolean forbidden, String message) {
        return new RuleTemplate(id, name, description,
                                List.of(field("target", forbidden ? "Must be empty" : "Required", ANY, ""),
                                        field("condition", "When", CONDITION, ""),
                                        new RuleParameter("value", "Is", Kind.VALUE, Set.of(), "condition")),
                                (filled, guarded) -> {
                                    Field target = filled.field("target");
                                    Field condition = filled.field("condition");
                                    RuleTemplate.Value value = filled.value("value");
                                    String equals = Filled.ref(condition) + " == " + value.cel();
                                    String when = guarded.test(condition)
                                            ? "(" + Filled.present(condition) + " && " + equals + ")"
                                            : "(" + equals + ")";
                                    String then = (forbidden ? "!" : "") + Filled.present(target);
                                    return new Rule("!" + when + " || " + then,
                                                    RuleTemplate.sentence(message.formatted(
                                                            RuleTemplate.label(target), RuleTemplate.label(condition),
                                                            value.display())));
                                });
    }
}
