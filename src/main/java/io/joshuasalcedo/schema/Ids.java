package io.joshuasalcedo.schema;

import java.util.Locale;
import java.util.regex.Pattern;

/** Model IDs: {@code [a-z0-9-]{3,63}}. */
public final class Ids {

    /** The regular expression every model ID matches. */
    public static final String ID = "[a-z0-9-]{3,63}";
    private static final Pattern ID_PATTERN = Pattern.compile(ID);

    private Ids() {
    }

    /**
     * Whether {@code id} has the shape of a model ID.
     *
     * @param id the text to check; may be null
     * @return true if it matches {@link #ID}
     */
    public static boolean isValid(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /**
     * Makes an ID from a display name: words split at case changes and non-letters, lower-cased and joined with
     * hyphens. {@code EmailAddress} becomes {@code email-address}, {@code Order line} becomes {@code order-line},
     * {@code HTTPRequest} becomes {@code http-request}. The result is cut to 63 characters but may still be too
     * short (under 3) to be valid.
     *
     * @param displayName the name as written in code; null gives an empty string
     * @return the derived ID
     */
    public static String fromDisplayName(String displayName) {
        if (displayName == null) {
            return "";
        }
        String kebab = displayName.strip()
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return kebab.length() > 63 ? kebab.substring(0, 63).replaceAll("-+$", "") : kebab;
    }
}
