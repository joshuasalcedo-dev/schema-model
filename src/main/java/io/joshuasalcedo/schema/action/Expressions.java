package io.joshuasalcedo.schema.action;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles and runs the CEL expressions of actions (rules, set steps) and models (invariants). The variables:
 * <ul>
 *   <li>{@code record}, {@code input}: in actions, the record's values and the action's input, by field name</li>
 *   <li>{@code linked}: in actions, the records they point to, by the field that points: {@code linked.plan.length}
 *       is the length of the plan that {@code input.plan} (or else {@code record.plan}) names</li>
 *   <li>{@code value}: in invariants, the values of the object being checked, by field name</li>
 *   <li>{@code now}: the current instant, {@code today}: the current date (UTC), both as ISO-8601 text</li>
 * </ul>
 * Besides CEL's own functions: {@code addDays(date, n)}, {@code addMonths(date, n)} and {@code daysBetween(from, to)}
 * (see {@link DateFunctions}).
 * Dates and instants are text written with a fixed width ({@link #timestamp(Instant)}), so comparing them as text
 * gives the same answer as comparing them in time, and a date compares correctly with an instant
 * ({@code value.birthDate <= today}). Compiled programs are cached, so running an expression again does not parse
 * it again.
 */
public final class Expressions {

    private static final CelCompiler COMPILER = CelCompilerFactory.standardCelCompilerBuilder()
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS) // has(), all(), exists(), map(), filter()
            .addVar("record", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("input", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("linked", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("value", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("now", SimpleType.STRING)
            .addVar("today", SimpleType.STRING)
            .addFunctionDeclarations(DateFunctions.declarations()) // addDays, addMonths, daysBetween
            .build();
    private static final CelRuntime RUNTIME = CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(DateFunctions.bindings())
            .build();
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);
    private static final Expressions SHARED = new Expressions();

    private final Map<String, CelRuntime.Program> programs = new ConcurrentHashMap<>();

    /** Creates an expression runner with an empty cache. Thread-safe; one per engine is enough. */
    public Expressions() {
    }

    /**
     * A runner shared by everything that has none of its own, e.g. invariant checks.
     *
     * @return the shared runner
     */
    public static Expressions shared() {
        return SHARED;
    }

    /**
     * An instant as expressions see it: UTC with all nine fraction digits, e.g.
     * {@code 2026-10-19T08:30:00.000000000Z}, so instants compare correctly as text.
     *
     * @param instant the instant
     * @return the fixed-width ISO-8601 text
     */
    public static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant);
    }

    /**
     * The {@code now} and {@code today} variables for a moment.
     *
     * @param now the moment
     * @return {@code now} as {@link #timestamp(Instant)} and {@code today} as its UTC date
     */
    public static Map<String, Object> clock(Instant now) {
        return Map.of("now", timestamp(now), "today", now.atOffset(ZoneOffset.UTC).toLocalDate().toString());
    }

    /**
     * Compiles an expression without running it, to report mistakes when a definition is saved.
     *
     * @param expression    the CEL expression
     * @param mustBeBoolean true for rules and invariants: the result must be true or false (or only known when it
     *                      runs)
     * @return why it does not compile, or empty when it does
     */
    public Optional<String> check(String expression, boolean mustBeBoolean) {
        try {
            CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
            if (mustBeBoolean && !ast.getResultType().equals(SimpleType.BOOL)
                && !ast.getResultType().equals(SimpleType.DYN)) {
                return Optional.of("must be true or false, but is " + ast.getResultType().name());
            }
            return Optional.empty();
        } catch (CelValidationException e) {
            return Optional.of(e.getMessage());
        }
    }

    /**
     * Runs an expression; the compiled program is cached by its text.
     *
     * @param expression the CEL expression
     * @param variables  the variables it uses, already in CEL types (see
     *                   {@link io.joshuasalcedo.schema.record.RecordValues#toExpressionValue})
     * @return the result as a CEL Java value: {@code Long}, {@code Double}, {@code String}, {@code Boolean},
     *         {@code Map}, {@code List} or CEL's null value
     * @throws IllegalArgumentException if it does not compile or fails while running, e.g. reading an absent field
     */
    public Object eval(String expression, Map<String, ?> variables) {
        try {
            return programs.computeIfAbsent(expression, Expressions::program).eval(variables);
        } catch (CelEvaluationException e) {
            throw new IllegalArgumentException(withCause(e), e);
        }
    }

    /** CEL's message, plus the reason a function of ours gave (e.g. "'next week' is not a date"), which CEL hides. */
    private static String withCause(Exception e) {
        // the first cause that is not CEL's own wrapping: our function's reason, not the library detail below it
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root && root.getClass().getName().startsWith("dev.cel")) {
            root = root.getCause();
        }
        String message = e.getMessage();
        return root != e && root.getMessage() != null && !message.contains(root.getMessage())
                ? message + ": " + root.getMessage() : message;
    }

    private static CelRuntime.Program program(String expression) {
        try {
            return RUNTIME.createProgram(COMPILER.compile(expression).getAst());
        } catch (CelValidationException | CelEvaluationException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
