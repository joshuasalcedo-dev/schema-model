package io.joshuasalcedo.schema.action;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelFunctionBinding;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Date arithmetic for expressions, on the ISO-8601 text dates and instants expressions see (an instant counts by its
 * UTC date):
 * <ul>
 *   <li>{@code addDays(date, n)}: the date n days later (n may be negative), e.g. {@code addDays(today, 7)}</li>
 *   <li>{@code addMonths(date, n)}: the date n months later; a day the month does not have becomes its last day
 *       ({@code addMonths('2026-01-31', 1)} is {@code '2026-02-28'})</li>
 *   <li>{@code daysBetween(from, to)}: whole days from one date to the other (negative when {@code to} is earlier),
 *       e.g. nights of a stay, days left on a membership</li>
 *   <li>{@code addMinutes(time, n)}, {@code addHours(time, n)}: the instant n minutes or hours later (a date counts
 *       from its UTC midnight), as fixed-width text, e.g. a session's end, "24 hours before it starts"</li>
 * </ul>
 * Every business needs these (a membership's end, a stay's nights, a due date), so they live in the engine rather
 * than in each app.
 */
final class DateFunctions {

    private DateFunctions() {
    }

    static List<CelFunctionDecl> declarations() {
        return List.of(
                CelFunctionDecl.newFunctionDeclaration("addDays",
                        CelOverloadDecl.newGlobalOverload("addDays_string_int", SimpleType.STRING, SimpleType.STRING, SimpleType.INT)),
                CelFunctionDecl.newFunctionDeclaration("addMonths",
                        CelOverloadDecl.newGlobalOverload("addMonths_string_int", SimpleType.STRING, SimpleType.STRING, SimpleType.INT)),
                CelFunctionDecl.newFunctionDeclaration("daysBetween",
                        CelOverloadDecl.newGlobalOverload("daysBetween_string_string", SimpleType.INT, SimpleType.STRING, SimpleType.STRING)),
                CelFunctionDecl.newFunctionDeclaration("addMinutes",
                        CelOverloadDecl.newGlobalOverload("addMinutes_string_int", SimpleType.STRING, SimpleType.STRING, SimpleType.INT)),
                CelFunctionDecl.newFunctionDeclaration("addHours",
                        CelOverloadDecl.newGlobalOverload("addHours_string_int", SimpleType.STRING, SimpleType.STRING, SimpleType.INT)));
    }

    static List<CelFunctionBinding> bindings() {
        return List.of(
                CelFunctionBinding.from("addDays_string_int", String.class, Long.class,
                        (date, days) -> date(date).plusDays(days).toString()),
                CelFunctionBinding.from("addMonths_string_int", String.class, Long.class,
                        (date, months) -> date(date).plusMonths(months).toString()),
                CelFunctionBinding.from("daysBetween_string_string", String.class, String.class,
                        (from, to) -> ChronoUnit.DAYS.between(date(from), date(to))),
                CelFunctionBinding.from("addMinutes_string_int", String.class, Long.class,
                        (at, minutes) -> Expressions.timestamp(instant(at).plus(minutes, ChronoUnit.MINUTES))),
                CelFunctionBinding.from("addHours_string_int", String.class, Long.class,
                        (at, hours) -> Expressions.timestamp(instant(at).plus(hours, ChronoUnit.HOURS))));
    }

    /** An instant, or a date's start (UTC midnight). */
    static Instant instant(String text) {
        try {
            return text.length() > 10 ? Instant.parse(text) : date(text).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("'" + text + "' is not a date or time (ISO-8601)", e);
        }
    }

    /** A date, or an instant's UTC date (its first ten characters, as {@link Expressions#timestamp} writes them). */
    static LocalDate date(String text) {
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("'" + text + "' is not a date (YYYY-MM-DD)", e);
        }
    }
}
