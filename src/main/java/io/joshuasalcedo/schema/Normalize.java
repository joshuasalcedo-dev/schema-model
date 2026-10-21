package io.joshuasalcedo.schema;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A clean-up applied to a text value before it is checked and stored, the way a Java value object normalises its
 * constructor argument. A field lists them in order ({@link Field.Builder#normalize}); they apply to {@link
 * FieldType#TEXT} and {@link FieldType#CHOICE} fields only. Each step leaves a value it cannot handle unchanged, so the
 * field's pattern still reports it.
 * <pre>{@code
 * Field.text("email").normalize(Normalize.TRIM, Normalize.LOWERCASE).pattern(".+@.+")
 * }</pre>
 */
public enum Normalize {

    /** Removes leading and trailing white space ({@link String#strip()}). */
    TRIM,
    /** Lower-cases, independent of the machine's locale. */
    LOWERCASE,
    /** Upper-cases, independent of the machine's locale. */
    UPPERCASE,
    /** Replaces every run of white space with one space. */
    COLLAPSE_SPACES,
    /** Keeps only the digits, e.g. {@code +63 (917) 123-4567} becomes {@code 639171234567}. */
    DIGITS_ONLY,
    /** Treats a blank value as no value at all, so an optional field stays absent instead of holding spaces. */
    BLANK_TO_ABSENT,
    /**
     * Writes an IPv6 literal in the one form {@code InetAddress} gives: eight groups of lower-case hexadecimal without
     * leading zeros, {@code ::} expanded ({@code 2001:DB8::1} becomes {@code 2001:db8:0:0:0:0:0:1}). Text that is not
     * an IPv6 literal is left as it is; no name lookup is ever made.
     */
    IPV6_CANONICAL,
    /**
     * Writes a MAC address as six upper-case byte pairs separated by colons, from the colon, hyphen, Cisco-dot or
     * plain hexadecimal forms ({@code 001a.2b3c.4d5e} becomes {@code 00:1A:2B:3C:4D:5E}).
     */
    MAC_CANONICAL,
    /**
     * Converts an internationalised domain name to its ASCII (punycode) form and lower-cases it
     * ({@code Bücher.example} becomes {@code xn--bcher-kva.example}), as {@code java.net.IDN} does.
     */
    IDN_ASCII;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern MAC_SEPARATORS = Pattern.compile("[-:.]");
    private static final Pattern HEX12 = Pattern.compile("[0-9A-Fa-f]{12}");

    /**
     * Applies this step.
     *
     * @param value the text so far
     * @return the cleaned text; null only for {@link #BLANK_TO_ABSENT} on blank text
     */
    public String apply(String value) {
        return switch (this) {
            case TRIM -> value.strip();
            case LOWERCASE -> value.toLowerCase(Locale.ROOT);
            case UPPERCASE -> value.toUpperCase(Locale.ROOT);
            case COLLAPSE_SPACES -> WHITESPACE.matcher(value).replaceAll(" ");
            case DIGITS_ONLY -> value.replaceAll("[^0-9]", "");
            case BLANK_TO_ABSENT -> value.isBlank() ? null : value;
            case IPV6_CANONICAL -> ipv6(value);
            case MAC_CANONICAL -> mac(value);
            case IDN_ASCII -> idn(value);
        };
    }

    /**
     * Applies several steps in order.
     *
     * @param steps the steps
     * @param value the text
     * @return the cleaned text, or null once a step made it absent
     */
    public static String applyAll(Iterable<Normalize> steps, String value) {
        String out = value;
        for (Normalize step : steps) {
            if (out == null) {
                return null;
            }
            out = step.apply(out);
        }
        return out;
    }

    private static String ipv6(String value) {
        // only an IPv6 literal (it has a colon) is parsed, so InetAddress never looks a name up
        if (value.indexOf(':') < 0 || !IPV6_CHARS.matcher(value).matches()) {
            return value;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address ? address.getHostAddress() : value;
        } catch (UnknownHostException | SecurityException e) {
            return value;
        }
    }

    private static String mac(String value) {
        String hex = MAC_SEPARATORS.matcher(value).replaceAll("");
        if (!HEX12.matcher(hex).matches()) {
            return value;
        }
        String upper = hex.toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) {
                out.append(':');
            }
            out.append(upper, i, i + 2);
        }
        return out.toString();
    }

    private static String idn(String value) {
        try {
            return IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
