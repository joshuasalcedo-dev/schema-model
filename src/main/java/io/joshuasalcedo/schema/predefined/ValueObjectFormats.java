package io.joshuasalcedo.schema.predefined;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.joshuasalcedo.schema.text.Labels;
import io.joshuasalcedo.schema.text.ValueFormats;

/**
 * How the {@link ValueObjects} look as text, the way their Java value objects print themselves:
 * <table>
 *   <caption>Examples</caption>
 *   <tr><th scope="col">Value object</th><th scope="col">Text</th></tr>
 *   <tr><td>money</td><td>PHP 1,234.50</td></tr>
 *   <tr><td>full-name</td><td>Juan Dela Cruz</td></tr>
 *   <tr><td>address</td><td>1 Ayala Ave, Makati 1226, Philippines</td></tr>
 *   <tr><td>phone-number</td><td>+63 9171234567</td></tr>
 *   <tr><td>coordinates</td><td>14.5547° N, 121.0244° E</td></tr>
 *   <tr><td>weight</td><td>1.25 kg</td></tr>
 *   <tr><td>date-range</td><td>Oct 18, 2026 – Oct 20, 2026</td></tr>
 *   <tr><td>time</td><td>10:05 PM</td></tr>
 *   <tr><td>recurrence</td><td>every 2 weeks</td></tr>
 *   <tr><td>semantic-version</td><td>1.2.3-rc.1+build.5</td></tr>
 *   <tr><td>color</td><td>#FF8000</td></tr>
 *   <tr><td>ssh-connection</td><td>deploy@10.0.0.5:22</td></tr>
 * </table>
 * Installed by {@link ValueFormats#defaults}; override any of them with {@link ValueFormats#register}.
 */
public final class ValueObjectFormats {

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("MILLIMETER", "mm"), Map.entry("CENTIMETER", "cm"), Map.entry("METER", "m"),
            Map.entry("KILOMETER", "km"), Map.entry("INCH", "in"), Map.entry("FOOT", "ft"), Map.entry("YARD", "yd"),
            Map.entry("MILE", "mi"), Map.entry("NAUTICAL_MILE", "nmi"),
            Map.entry("MILLIGRAM", "mg"), Map.entry("GRAM", "g"), Map.entry("KILOGRAM", "kg"),
            Map.entry("METRIC_TON", "t"), Map.entry("OUNCE", "oz"), Map.entry("POUND", "lb"), Map.entry("STONE", "st"),
            Map.entry("US_TON", "US ton"),
            Map.entry("BYTE", "B"), Map.entry("BIT", "bit"), Map.entry("KIBIBYTE", "KiB"), Map.entry("MEBIBYTE", "MiB"),
            Map.entry("GIBIBYTE", "GiB"), Map.entry("TEBIBYTE", "TiB"), Map.entry("PEBIBYTE", "PiB"),
            Map.entry("KILOBYTE", "kB"), Map.entry("MEGABYTE", "MB"), Map.entry("GIGABYTE", "GB"),
            Map.entry("TERABYTE", "TB"), Map.entry("PETABYTE", "PB"));

    private static final Map<String, String[]> FREQUENCIES = Map.of(
            "DAILY", new String[]{"day", "days"}, "WEEKLY", new String[]{"week", "weeks"},
            "MONTHLY", new String[]{"month", "months"}, "QUARTERLY", new String[]{"quarter", "quarters"},
            "YEARLY", new String[]{"year", "years"});

    private ValueObjectFormats() {
    }

    /**
     * Adds the format of every predefined value object.
     *
     * @param formats the formats to add them to
     */
    public static void register(ValueFormats formats) {
        // contact
        formats.register("username", (v, f) -> s(v, "value"));
        formats.register("email-address", (v, f) -> s(v, "value"));
        formats.register("full-name", (v, f) -> join(" ", s(v, "firstName"), s(v, "middleName"), s(v, "lastName")));
        formats.register("phone-number", (v, f) -> "+" + s(v, "countryCode") + " " + s(v, "nationalNumber"));
        formats.register("link", (v, f) -> s(v, "value"));
        formats.register("address", (v, f) -> join(", ", s(v, "street"), s(v, "addressLine2"),
                                                   join(" ", s(v, "city"), s(v, "postalCode")),
                                                   s(v, "stateOrProvince"), country(s(v, "country"))));
        // financial
        formats.register("currency", (v, f) -> s(v, "code"));
        formats.register("money", (v, f) -> join(" ", s(nested(v, "currency"), "code"), number(f, v.get("amount"))));
        formats.register("percentage", (v, f) -> number(f, v.get("value"), true) + "%");
        formats.register("crypto-currency-address", (v, f) -> s(v, "coin") + " " + s(v, "address"));
        // geo
        formats.register("coordinates", (v, f) -> degrees(v.get("latitude"), "N", "S") + ", "
                                                  + degrees(v.get("longitude"), "E", "W"));
        formats.register("postal-code", (v, f) -> s(v, "value"));
        // identifier
        formats.register("slug", (v, f) -> s(v, "value"));
        // measurement
        for (String id : List.of("distance", "weight", "file-size")) {
            formats.register(id, (v, f) -> number(f, v.get("value"), true) + " " + unit(s(v, "unit")));
        }
        // network
        for (String id : List.of("domain-name", "ipv4-address", "ipv6-address", "ip-address", "mac-address")) {
            formats.register(id, (v, f) -> s(v, "value"));
        }
        formats.register("port", (v, f) -> s(v, "value"));
        formats.register("ssh-connection", (v, f) -> {
            String host = s(nested(v, "host"), "value");
            return s(v, "username") + "@" + (host.contains(":") ? "[" + host + "]" : host) + ":"
                   + s(nested(v, "port"), "value");
        });
        // temporal
        formats.register("date-of-birth", (v, f) -> date(f, v.get("value")));
        formats.register("date-range", (v, f) -> date(f, v.get("startDate")) + " – " + date(f, v.get("endDate")));
        formats.register("date-time-with-zone", (v, f) -> s(v, "value"));
        formats.register("duration-nano", (v, f) -> duration(v.get("nanos")));
        formats.register("effective-period", (v, f) -> v.get("through") == null
                ? "from " + timestamp(f, v.get("from"))
                : timestamp(f, v.get("from")) + " – " + timestamp(f, v.get("through")));
        formats.register("recurrence", (v, f) -> recurrence(s(v, "frequency"), v.get("interval")));
        formats.register("time", (v, f) -> time(v));
        formats.register("time-block", (v, f) -> time(nested(v, "start")) + " – " + time(nested(v, "end")));
        formats.register("timestamp", (v, f) -> timestamp(f, v.get("value")));
        // versioning
        formats.register("semantic-version", (v, f) -> s(v, "major") + "." + s(v, "minor") + "." + s(v, "patch")
                                                       + (v.get("preRelease") == null ? "" : "-" + s(v, "preRelease"))
                                                       + (v.get("buildMetadata") == null ? ""
                                                               : "+" + s(v, "buildMetadata")));
        // visual
        formats.register("color", (v, f) -> color(v));
        // general
        formats.register("count", (v, f) -> number(f, v.get("value")));
        // media
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

    /**
     * The color as hex, {@code #RRGGBB}, with {@code AA} added when it is not opaque.
     *
     * @param values the color's values; absent components default as the Java Color does (0, alpha 255)
     * @return the hex text
     */
    public static String color(Map<String, ?> values) {
        int r = component(values.get("red"), 0);
        int g = component(values.get("green"), 0);
        int b = component(values.get("blue"), 0);
        int a = component(values.get("alpha"), 255);
        return a == 255 ? String.format("#%02X%02X%02X", r, g, b) : String.format("#%02X%02X%02X%02X", r, g, b, a);
    }

    /**
     * The symbol of a measurement unit constant, e.g. {@code KILOGRAM} gives {@code kg}.
     *
     * @param unit the constant
     * @return the symbol, or the constant in lower case when it has none
     */
    public static String unit(String unit) {
        return UNITS.getOrDefault(unit, unit.toLowerCase(Locale.ROOT));
    }

    // --- helpers -------------------------------------------------------------------------------------------

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

    private static String country(String constant) {
        return constant.isEmpty() || constant.equals("UNKNOWN") ? "" : Labels.choice(constant);
    }

    private static String number(ValueFormats formats, Object value) {
        return number(formats, value, false);
    }

    /** With {@code trim}, trailing zeros are dropped (12.5000% shows as 12.5%). */
    private static String number(ValueFormats formats, Object value, boolean trim) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal d) {
            BigDecimal shown = trim ? d.stripTrailingZeros() : d;
            return formats.number(shown.scale() < 0 ? shown.setScale(0, RoundingMode.UNNECESSARY) : shown);
        }
        return value instanceof Number n ? formats.number(n) : String.valueOf(value);
    }

    private static String degrees(Object value, String positive, String negative) {
        BigDecimal d = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        return d.abs().stripTrailingZeros().toPlainString() + "° " + (d.signum() < 0 ? negative : positive);
    }

    private static String date(ValueFormats formats, Object value) {
        if (value == null) {
            return "";
        }
        return formats.date(value instanceof LocalDate d ? d : LocalDate.parse(String.valueOf(value)));
    }

    private static String timestamp(ValueFormats formats, Object value) {
        if (value == null) {
            return "";
        }
        return formats.timestamp(value instanceof Instant i ? i : Instant.parse(String.valueOf(value)));
    }

    private static String time(Map<String, ?> values) {
        return s(values, "hour") + ":" + String.format("%02d", component(values.get("minute"), 0)) + " "
               + s(values, "meridiem");
    }

    private static String recurrence(String frequency, Object interval) {
        if (frequency.equals("ONCE")) {
            return "once";
        }
        String[] unit = FREQUENCIES.get(frequency);
        if (unit == null) {
            return Labels.choice(frequency);
        }
        long n = interval instanceof Number num ? num.longValue() : 1;
        return n == 1 ? "every " + unit[0] : "every " + n + " " + unit[1];
    }

    private static String duration(Object nanos) {
        long n = nanos instanceof Number num ? num.longValue() : 0;
        String[] units = {"ns", "µs", "ms", "s"};
        double value = n;
        int i = 0;
        while (value >= 1000 && i < units.length - 1) {
            value /= 1000;
            i++;
        }
        if (i == units.length - 1 && value >= 60) {
            long seconds = n / 1_000_000_000L;
            return seconds >= 3600 ? (seconds / 3600) + "h " + (seconds % 3600 / 60) + "m"
                                   : (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        BigDecimal shown = BigDecimal.valueOf(value).setScale(i == 0 ? 0 : 2, RoundingMode.HALF_EVEN)
                .stripTrailingZeros();
        return shown.toPlainString() + " " + units[i];
    }

    private static int component(Object value, int absent) {
        return value instanceof Number n ? n.intValue() : absent;
    }
}
