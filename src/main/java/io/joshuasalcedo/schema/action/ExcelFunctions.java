package io.joshuasalcedo.schema.action;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelFunctionOverload;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spreadsheet-style functions for expressions, so people who write Excel formulas can write rules and calculations:
 * <ul>
 *   <li>Numbers: {@code SUM(list)}, {@code AVG(list)}, {@code MIN(list)}, {@code MAX(list)}, {@code COUNT(list)},
 *       {@code ROUND(x)}, {@code ROUND(x, digits)}, {@code FLOOR(x)}, {@code CEIL(x)}, {@code ABS(x)},
 *       {@code PERCENT(x, p)} (p percent of x)</li>
 *   <li>Logic: {@code IF(condition, then, else)}, {@code IS_EMPTY(x)} (null, empty text, empty list or map),
 *       {@code COALESCE(a, b)}, {@code COALESCE(a, b, c)} (the first that is not empty), {@code EXISTS(list)}</li>
 *   <li>Dates (ISO-8601 text): {@code DATE_ADD(date, n, unit)}, {@code DATE_SUB(date, n, unit)} with unit
 *       {@code 'D'}, {@code 'W'}, {@code 'M'}, {@code 'Y'} (a date) or {@code 'H'}, {@code 'MIN'} (an instant);
 *       {@code DAYS_BETWEEN(from, to)}, {@code MONTHS_BETWEEN(from, to)}, {@code HOURS(from, to)},
 *       {@code MINUTES(from, to)}; {@code HOURS(duration)}, {@code MINUTES(duration)} of an ISO duration (PT4H30M);
 *       {@code YEAR(date)}, {@code MONTH(date)}, {@code DAY(date)}; {@code START_OF(date, unit)},
 *       {@code END_OF(date, unit)} with unit {@code 'M'} or {@code 'Y'}</li>
 *   <li>Text: {@code CONCAT(a, b)}, {@code CONCAT(list)}, {@code TRIM(x)}, {@code UPPER(x)}, {@code LOWER(x)},
 *       {@code LENGTH(x)} (of text or a list), {@code TO_CASE(x, 'snake')} (snake, kebab, camel, pascal, constant,
 *       title, sentence, lower, upper: see {@link io.joshuasalcedo.schema.TextCase}), {@code FORMAT(x, pattern)} (a
 *       number with a DecimalFormat pattern such as {@code '#,##0.00'}, a date or instant with a DateTimeFormatter
 *       pattern such as {@code 'dd MMM yyyy'}), {@code IN_WORDS(x)} (a number in English words, for cheques and
 *       invoices: {@code 'one thousand two hundred and 50/100'})</li>
 *   <li>Money: {@code MONEY(amount, currency)} (a Money value), {@code AMOUNT(money)}, {@code CURRENCY_OF(money)}</li>
 * </ul>
 * They take any value ({@code dyn}) and mix whole and decimal numbers freely, as a spreadsheet does; empty values
 * are skipped by the list functions and make the others return empty. CEL's own operators still apply
 * ({@code a > b ? x : y}, {@code lines.map(l, l.net)}), so a sum over a component list is
 * {@code SUM(record.lines.map(l, l.net))} and a filtered one {@code SUM(record.lines.filter(l, l.paid).map(l, l.net))}.
 * {@code IF} evaluates both branches; use {@code ? :} when a branch could fail.
 */
final class ExcelFunctions {

    private static final CelType DYN = SimpleType.DYN;
    private static final CelType LIST = ListType.create(SimpleType.DYN);

    private ExcelFunctions() {
    }

    private record Fn(String name, String overload, CelType result, List<CelType> args, CelFunctionOverload impl) {
    }

    private static final List<Fn> FUNCTIONS = List.of(
            // ---- numbers
            fn("SUM", "list", DYN, List.of(LIST), a -> sum(list(a[0]))),
            fn("AVG", "list", DYN, List.of(LIST), a -> {
                List<BigDecimal> xs = numbers(list(a[0]));
                return xs.isEmpty() ? empty() : number(total(xs).divide(BigDecimal.valueOf(xs.size()), 10, RoundingMode.HALF_EVEN));
            }),
            fn("MIN", "list", DYN, List.of(LIST), a -> numbers(list(a[0])).stream().min(BigDecimal::compareTo).map(ExcelFunctions::number).orElse(empty())),
            fn("MAX", "list", DYN, List.of(LIST), a -> numbers(list(a[0])).stream().max(BigDecimal::compareTo).map(ExcelFunctions::number).orElse(empty())),
            fn("COUNT", "list", SimpleType.INT, List.of(LIST), a -> (long) list(a[0]).stream().filter(x -> !isEmpty(x)).count()),
            fn("ROUND", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : number(decimal(a[0]).setScale(0, RoundingMode.HALF_UP))),
            fn("ROUND", "dyn_dyn", DYN, List.of(DYN, DYN), a -> isEmpty(a[0]) ? empty()
                    : number(decimal(a[0]).setScale(decimal(a[1]).intValue(), RoundingMode.HALF_UP))),
            fn("FLOOR", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : number(decimal(a[0]).setScale(0, RoundingMode.FLOOR))),
            fn("CEIL", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : number(decimal(a[0]).setScale(0, RoundingMode.CEILING))),
            fn("ABS", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : number(decimal(a[0]).abs())),
            fn("PERCENT", "dyn_dyn", DYN, List.of(DYN, DYN), a -> isEmpty(a[0]) || isEmpty(a[1]) ? empty()
                    : number(decimal(a[0]).multiply(decimal(a[1])).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN))),
            // ---- logic
            fn("IF", "dyn_dyn_dyn", DYN, List.of(SimpleType.BOOL, DYN, DYN), a -> Boolean.TRUE.equals(a[0]) ? a[1] : a[2]),
            fn("IS_EMPTY", "dyn", SimpleType.BOOL, List.of(DYN), a -> isEmpty(a[0])),
            fn("EXISTS", "list", SimpleType.BOOL, List.of(LIST), a -> list(a[0]).stream().anyMatch(x -> !isEmpty(x))),
            fn("COALESCE", "dyn_dyn", DYN, List.of(DYN, DYN), a -> firstNonEmpty(a)),
            fn("COALESCE", "dyn_dyn_dyn", DYN, List.of(DYN, DYN, DYN), a -> firstNonEmpty(a)),
            // ---- dates
            fn("DATE_ADD", "dyn_dyn_dyn", SimpleType.STRING, List.of(DYN, DYN, DYN), a -> shift(a[0], decimal(a[1]).longValue(), text(a[2]))),
            fn("DATE_SUB", "dyn_dyn_dyn", SimpleType.STRING, List.of(DYN, DYN, DYN), a -> shift(a[0], -decimal(a[1]).longValue(), text(a[2]))),
            fn("DAYS_BETWEEN", "dyn_dyn", SimpleType.INT, List.of(DYN, DYN), a -> ChronoUnit.DAYS.between(DateFunctions.date(text(a[0])), DateFunctions.date(text(a[1])))),
            fn("MONTHS_BETWEEN", "dyn_dyn", SimpleType.INT, List.of(DYN, DYN), a -> ChronoUnit.MONTHS.between(DateFunctions.date(text(a[0])), DateFunctions.date(text(a[1])))),
            fn("HOURS", "dyn_dyn", SimpleType.DOUBLE, List.of(DYN, DYN), a -> minutes(a[0], a[1]) / 60.0),
            fn("MINUTES", "dyn_dyn", SimpleType.DOUBLE, List.of(DYN, DYN), a -> (double) minutes(a[0], a[1])),
            fn("HOURS", "dyn", SimpleType.DOUBLE, List.of(DYN), a -> duration(text(a[0])).toSeconds() / 3600.0),
            fn("MINUTES", "dyn", SimpleType.DOUBLE, List.of(DYN), a -> duration(text(a[0])).toSeconds() / 60.0),
            fn("YEAR", "dyn", SimpleType.INT, List.of(DYN), a -> (long) DateFunctions.date(text(a[0])).getYear()),
            fn("MONTH", "dyn", SimpleType.INT, List.of(DYN), a -> (long) DateFunctions.date(text(a[0])).getMonthValue()),
            fn("DAY", "dyn", SimpleType.INT, List.of(DYN), a -> (long) DateFunctions.date(text(a[0])).getDayOfMonth()),
            fn("START_OF", "dyn_dyn", SimpleType.STRING, List.of(DYN, DYN), a -> boundary(text(a[0]), text(a[1]), true)),
            fn("END_OF", "dyn_dyn", SimpleType.STRING, List.of(DYN, DYN), a -> boundary(text(a[0]), text(a[1]), false)),
            // ---- text
            fn("CONCAT", "dyn_dyn", SimpleType.STRING, List.of(DYN, DYN), a -> textOrBlank(a[0]) + textOrBlank(a[1])),
            fn("CONCAT", "list", SimpleType.STRING, List.of(LIST), a -> String.join("", list(a[0]).stream().map(ExcelFunctions::textOrBlank).toList())),
            fn("TRIM", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : text(a[0]).strip()),
            fn("UPPER", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : text(a[0]).toUpperCase(Locale.ROOT)),
            fn("LOWER", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : text(a[0]).toLowerCase(Locale.ROOT)),
            fn("TO_CASE", "dyn_dyn", DYN, List.of(DYN, DYN), a -> isEmpty(a[0]) ? empty()
                    : io.joshuasalcedo.schema.TextCase.of(text(a[1])).apply(text(a[0]))),
            fn("FORMAT", "dyn_dyn", DYN, List.of(DYN, DYN), a -> isEmpty(a[0]) ? empty() : format(a[0], text(a[1]))),
            fn("IN_WORDS", "dyn", DYN, List.of(DYN), a -> isEmpty(a[0]) ? empty() : NumberWords.of(decimal(a[0]))),
            fn("LENGTH", "dyn", SimpleType.INT, List.of(DYN), a -> switch (a[0]) {
                case Collection<?> c -> (long) c.size();
                case Map<?, ?> m -> (long) m.size();
                case Object o when isEmpty(o) -> 0L;
                case Object o -> (long) text(o).length();
            }),
            // ---- money
            fn("MONEY", "dyn_dyn", DYN, List.of(DYN, DYN), a -> {
                Map<String, Object> money = new LinkedHashMap<>();
                money.put("amount", isEmpty(a[0]) ? 0.0 : decimal(a[0]).doubleValue());
                money.put("currency", text(a[1]));
                return money;
            }),
            fn("AMOUNT", "dyn", DYN, List.of(DYN), a -> a[0] instanceof Map<?, ?> m && m.containsKey("amount") ? m.get("amount") : empty()),
            fn("CURRENCY_OF", "dyn", DYN, List.of(DYN), a -> a[0] instanceof Map<?, ?> m && m.containsKey("currency") ? m.get("currency") : empty()));

    private static Fn fn(String name, String suffix, CelType result, List<CelType> args, CelFunctionOverload impl) {
        return new Fn(name, name + "_" + suffix, result, args, impl);
    }

    static List<CelFunctionDecl> declarations() {
        Map<String, List<CelOverloadDecl>> byName = new LinkedHashMap<>();
        for (Fn f : FUNCTIONS) {
            byName.computeIfAbsent(f.name(), k -> new ArrayList<>())
                    .add(CelOverloadDecl.newGlobalOverload(f.overload(), f.result(), f.args()));
        }
        return byName.entrySet().stream()
                .map(e -> CelFunctionDecl.newFunctionDeclaration(e.getKey(), e.getValue()))
                .toList();
    }

    static List<CelFunctionBinding> bindings() {
        return FUNCTIONS.stream()
                .map(f -> CelFunctionBinding.from(f.overload(), f.args().stream().<Class<?>>map(t -> Object.class).toList(), f.impl()))
                .toList();
    }

    /** The names, for documentation and for checking formulas that call an unknown function. */
    static List<String> names() {
        return FUNCTIONS.stream().map(Fn::name).distinct().toList();
    }

    // ---- values ---------------------------------------------------------------------------------------------

    static boolean isEmpty(Object o) {
        return switch (o) {
            case null -> true;
            case String s -> s.isBlank();
            case Collection<?> c -> c.isEmpty();
            case Map<?, ?> m -> m.isEmpty();
            default -> o.getClass().getSimpleName().equals("NullValue");
        };
    }

    /** CEL's null, which a function returns for "empty". */
    private static Object empty() {
        return Expressions.celNull();
    }

    private static Object firstNonEmpty(Object[] values) {
        return Arrays.stream(values).filter(v -> !isEmpty(v)).findFirst().orElse(empty());
    }

    private static List<?> list(Object o) {
        if (o instanceof Collection<?> c) {
            return List.copyOf(c.stream().filter(x -> x != null).toList());
        }
        return isEmpty(o) ? List.of() : List.of(o);
    }

    private static List<BigDecimal> numbers(List<?> values) {
        return values.stream().filter(v -> !isEmpty(v)).map(ExcelFunctions::decimal).toList();
    }

    private static BigDecimal total(List<BigDecimal> xs) {
        return xs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Object sum(List<?> values) {
        List<?> present = values.stream().filter(v -> !isEmpty(v)).toList();
        if (present.stream().allMatch(v -> v instanceof Long)) {
            return present.stream().mapToLong(v -> (Long) v).sum();
        }
        return number(total(numbers(present)));
    }

    /** A number as CEL takes it: whole numbers with no fraction stay whole only when given whole; others are doubles. */
    private static Object number(BigDecimal d) {
        return d.doubleValue();
    }

    static BigDecimal decimal(Object o) {
        return switch (o) {
            case Long l -> BigDecimal.valueOf(l);
            case Integer i -> BigDecimal.valueOf(i);
            case Double d -> BigDecimal.valueOf(d);
            case BigDecimal b -> b;
            case Number n -> new BigDecimal(n.toString());
            case Map<?, ?> m when m.get("amount") != null -> decimal(m.get("amount"));
            case String s -> {
                try {
                    yield new BigDecimal(s.strip());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + s + "' is not a number", e);
                }
            }
            case null, default -> throw new IllegalArgumentException(o + " is not a number");
        };
    }

    private static String text(Object o) {
        if (isEmpty(o)) {
            throw new IllegalArgumentException("a value is empty");
        }
        return o.toString();
    }

    private static String textOrBlank(Object o) {
        if (isEmpty(o)) {
            return "";
        }
        return o instanceof Double d && d == Math.rint(d) && !d.isInfinite() ? Long.toString(d.longValue()) : o.toString();
    }

    private static Object shift(Object date, long n, String unit) {
        if (isEmpty(date)) {
            return empty();
        }
        String d = text(date);
        return switch (unit.toUpperCase(Locale.ROOT)) {
            case "D" -> DateFunctions.date(d).plusDays(n).toString();
            case "W" -> DateFunctions.date(d).plusWeeks(n).toString();
            case "M" -> DateFunctions.date(d).plusMonths(n).toString();
            case "Y" -> DateFunctions.date(d).plusYears(n).toString();
            case "H" -> Expressions.timestamp(DateFunctions.instant(d).plus(n, ChronoUnit.HOURS));
            case "MIN" -> Expressions.timestamp(DateFunctions.instant(d).plus(n, ChronoUnit.MINUTES));
            default -> throw new IllegalArgumentException("'" + unit + "' is not a unit (D, W, M, Y, H, MIN)");
        };
    }

    private static String format(Object value, String pattern) {
        if (value instanceof Number || value instanceof Map<?, ?>) {
            java.text.DecimalFormat f = new java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
            f.setRoundingMode(RoundingMode.HALF_UP);
            return f.format(decimal(value));
        }
        String t = text(value);
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        return t.length() > 10
                ? f.format(DateFunctions.instant(t).atOffset(java.time.ZoneOffset.UTC))
                : f.format(DateFunctions.date(t));
    }

    private static long minutes(Object from, Object to) {
        return ChronoUnit.MINUTES.between(DateFunctions.instant(text(from)), DateFunctions.instant(text(to)));
    }

    /** An ISO duration; days and weeks count as 24 hours, months and years are refused (they have no fixed length). */
    private static Duration duration(String iso) {
        String upper = iso.toUpperCase(Locale.ROOT);
        int t = upper.indexOf('T');
        String datePart = t < 0 ? upper : upper.substring(0, t);
        String timePart = t < 0 ? "" : upper.substring(t);
        Duration out = Duration.ZERO;
        if (datePart.length() > 1) {
            Period p = Period.parse(datePart);
            if (p.getMonths() != 0 || p.getYears() != 0) {
                throw new IllegalArgumentException("'" + iso + "' has months or years, which have no fixed number of hours");
            }
            out = out.plusDays(p.getDays());
        }
        if (!timePart.isEmpty()) {
            out = out.plus(Duration.parse("P" + timePart));
        }
        return out;
    }

    private static String boundary(String date, String unit, boolean start) {
        LocalDate d = DateFunctions.date(date);
        return switch (unit.toUpperCase(Locale.ROOT)) {
            case "M" -> (start ? d.withDayOfMonth(1) : YearMonth.from(d).atEndOfMonth()).toString();
            case "Y" -> (start ? d.withDayOfYear(1) : d.withDayOfYear(d.lengthOfYear())).toString();
            default -> throw new IllegalArgumentException("'" + unit + "' is not a unit (M, Y)");
        };
    }
}
