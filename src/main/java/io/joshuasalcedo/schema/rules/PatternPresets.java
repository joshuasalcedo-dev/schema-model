package io.joshuasalcedo.schema.rules;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import io.joshuasalcedo.schema.Normalize;

/**
 * The catalog of {@link PatternPreset}s for text fields: email, URL, codes, identifiers and the like.
 * <pre>{@code
 * PatternPreset email = PatternPresets.byId("email").orElseThrow();
 * Field.text("email").pattern(email.pattern()).normalize(email.normalize().toArray(Normalize[]::new));
 * PatternPresets.recognize(field.pattern());   // which preset a field uses, if any
 * }</pre>
 */
public final class PatternPresets {

    private static final String OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final String NUMBER = "(?:0|[1-9][0-9]*)";
    private static final String DOTTED = "[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*";

    private static final List<PatternPreset> ALL = List.of(
            preset("email", "Email address", "An email address such as ana@example.com; stored in lower case.",
                   "[^@\\s]+@[^@\\s]+\\.[^@\\s]+", Normalize.TRIM, Normalize.LOWERCASE),
            preset("url", "Web address", "An http or https address such as https://example.com/page.",
                   "https?://[^\\s/?#]+[^\\s]*", Normalize.TRIM),
            preset("digits", "Digits only", "One or more digits 0-9, e.g. an account number.",
                   "[0-9]+", Normalize.TRIM),
            preset("letters", "Letters only", "One or more letters A-Z, upper or lower case.",
                   "[A-Za-z]+", Normalize.TRIM),
            preset("alphanumeric", "Letters and digits", "One or more letters A-Z and digits 0-9.",
                   "[A-Za-z0-9]+", Normalize.TRIM),
            preset("slug", "Slug", "Lower-case words of letters and digits joined by hyphens, e.g. summer-sale-2026.",
                   "[a-z0-9]+(?:-[a-z0-9]+)*", Normalize.TRIM, Normalize.LOWERCASE),
            preset("code", "Upper-case code", "Upper-case letters, digits and underscores, e.g. ORDER_PLACED.",
                   "[A-Z0-9_]+", Normalize.TRIM, Normalize.UPPERCASE),
            preset("hex-color", "Hex colour", "A colour as # and six hexadecimal digits, e.g. #1A2B3C.",
                   "#[0-9A-Fa-f]{6}", Normalize.TRIM),
            preset("phone-e164", "Phone number (E.164)", "7 to 15 digits with an optional leading +, "
                                                         + "e.g. +639171234567.",
                   "\\+?[0-9]{7,15}", Normalize.TRIM),
            preset("uuid", "UUID", "A UUID in its usual 8-4-4-4-12 hexadecimal form; stored in lower case.",
                   "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Normalize.TRIM,
                   Normalize.LOWERCASE),
            preset("ipv4", "IPv4 address", "Four numbers 0-255 separated by dots, e.g. 192.168.1.10.",
                   "(?:" + OCTET + "\\.){3}" + OCTET, Normalize.TRIM),
            preset("semver", "Semantic version", "A version such as 1.4.2 or 2.0.0-rc.1+build.5.",
                   NUMBER + "\\." + NUMBER + "\\." + NUMBER + "(?:-" + DOTTED + ")?(?:\\+" + DOTTED + ")?",
                   Normalize.TRIM),
            preset("country-code", "Country code", "An ISO 3166-1 alpha-2 country code such as PH or DE.",
                   "[A-Z]{2}", Normalize.TRIM, Normalize.UPPERCASE),
            preset("currency-code", "Currency code", "An ISO 4217 currency code such as USD or EUR.",
                   "[A-Z]{3}", Normalize.TRIM, Normalize.UPPERCASE));

    private static final Map<String, PatternPreset> BY_ID =
            ALL.stream().collect(Collectors.toUnmodifiableMap(PatternPreset::id, p -> p));

    private PatternPresets() {
    }

    private static PatternPreset preset(String id, String name, String description, String pattern,
                                        Normalize... normalize) {
        return new PatternPreset(id, name, description, pattern, List.of(normalize));
    }

    /**
     * Every preset, in display order.
     *
     * @return the presets
     */
    public static List<PatternPreset> all() {
        return ALL;
    }

    /**
     * Looks up a preset by its ID.
     *
     * @param id e.g. {@code email}
     * @return the preset, or empty for an unknown ID
     */
    public static Optional<PatternPreset> byId(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    /**
     * The preset whose pattern is exactly this text (ignoring leading and trailing white space), e.g. to show a
     * field's pattern by its name.
     *
     * @param pattern a field's pattern
     * @return the preset, or empty when none has that pattern
     */
    public static Optional<PatternPreset> recognize(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return Optional.empty();
        }
        String wanted = pattern.strip();
        return ALL.stream().filter(p -> p.pattern().equals(wanted)).findFirst();
    }
}
