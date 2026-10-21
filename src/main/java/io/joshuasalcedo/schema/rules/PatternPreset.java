package io.joshuasalcedo.schema.rules;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import io.joshuasalcedo.schema.Normalize;

/**
 * A named regular expression for {@link io.joshuasalcedo.schema.FieldType#TEXT} fields, with the clean-ups that suit it, so a
 * field can be given a pattern by picking "Email address" instead of writing one. The catalog is in
 * {@link PatternPresets}.
 *
 * @param id          stable ID, e.g. {@code email}
 * @param name        for people, e.g. "Email address"
 * @param description what values it accepts, in one sentence
 * @param pattern     a {@link Pattern} expression a value must match in full, as {@link io.joshuasalcedo.schema.Field#pattern()}
 * @param normalize   the clean-ups suggested for the field, applied before the pattern is checked
 */
public record PatternPreset(String id, String name, String description, String pattern, List<Normalize> normalize) {

    /** Copies the clean-ups so they cannot change and replaces null text with empty strings. */
    public PatternPreset {
        id = Objects.requireNonNullElse(id, "");
        name = Objects.requireNonNullElse(name, "");
        description = Objects.requireNonNullElse(description, "");
        pattern = Objects.requireNonNullElse(pattern, "");
        normalize = normalize == null ? List.of() : List.copyOf(normalize);
    }

    /**
     * Whether a value would be accepted by a field with this preset: the clean-ups are applied, then the pattern
     * must match in full.
     *
     * @param value the text as typed
     * @return true when it is accepted
     */
    public boolean matches(String value) {
        String cleaned = Normalize.applyAll(normalize, value);
        return cleaned != null && Pattern.compile(pattern).matcher(cleaned).matches();
    }
}
