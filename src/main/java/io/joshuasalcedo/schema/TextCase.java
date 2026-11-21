package io.joshuasalcedo.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How words are joined and capitalised in a text value: {@code metro_station}, {@code metro-station},
 * {@code metroStation}, {@code MetroStation}, {@code METRO_STATION}, {@code Metro Station}, {@code Metro station}.
 * Any of these reads any other: words are found at spaces, underscores, hyphens, dots and lower-to-upper changes
 * ({@code metroStation} is two words), so {@code TITLE.apply("metro_station")} is {@code "Metro Station"}.
 * <p>
 * Used three ways: a field converts what users type ({@link Normalize#SNAKE_CASE} and the others), a pattern
 * preset only accepts text already in a case ({@code snake-case}, ...), and expressions format a value for display
 * ({@code TO_CASE(record.type, 'title')}).
 */
public enum TextCase {

    /** {@code metro_station} */
    SNAKE("snake", "[a-z0-9]+(?:_[a-z0-9]+)*"),
    /** {@code metro-station} */
    KEBAB("kebab", "[a-z0-9]+(?:-[a-z0-9]+)*"),
    /** {@code metroStation} */
    CAMEL("camel", "[a-z][a-z0-9]*(?:[A-Z][a-z0-9]*)*"),
    /** {@code MetroStation} */
    PASCAL("pascal", "(?:[A-Z][a-z0-9]*)+"),
    /** {@code METRO_STATION} */
    CONSTANT("constant", "[A-Z0-9]+(?:_[A-Z0-9]+)*"),
    /** {@code Metro Station} */
    TITLE("title", "\\S+(?: \\S+)*"),
    /** {@code Metro station} */
    SENTENCE("sentence", "\\S+(?: \\S+)*"),
    /** {@code metro station} */
    LOWER("lower", "[^A-Z]*"),
    /** {@code METRO STATION} */
    UPPER("upper", "[^a-z]*");

    private static final Pattern SEPARATORS = Pattern.compile("[\\s_.\\-]+");

    private final String code;
    private final String pattern;

    TextCase(String code, String pattern) {
        this.code = code;
        this.pattern = pattern;
    }

    /**
     * The short name expressions use: snake, kebab, camel, pascal, constant, title, sentence, lower, upper.
     *
     * @return the code
     */
    public String code() {
        return code;
    }

    /**
     * A full-match regular expression for text already in this case.
     *
     * @return the pattern
     */
    public String pattern() {
        return pattern;
    }

    /**
     * The case with this code, ignoring letter case.
     *
     * @param code snake, kebab, camel, pascal, constant, title, sentence, lower or upper
     * @return the case
     * @throws IllegalArgumentException for an unknown code
     */
    public static TextCase of(String code) {
        for (TextCase c : values()) {
            if (c.code.equalsIgnoreCase(code == null ? "" : code.strip())) {
                return c;
            }
        }
        throw new IllegalArgumentException("'" + code + "' is not a text case (snake, kebab, camel, pascal, constant, "
                                           + "title, sentence, lower, upper)");
    }

    /**
     * Writes text in this case.
     *
     * @param text any text
     * @return the text in this case; blank text stays as it is
     */
    public String apply(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (this == LOWER) {
            return text.toLowerCase(Locale.ROOT);
        }
        if (this == UPPER) {
            return text.toUpperCase(Locale.ROOT);
        }
        List<String> words = words(text);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i).toLowerCase(Locale.ROOT);
            switch (this) {
                case SNAKE -> out.append(i > 0 ? "_" : "").append(w);
                case KEBAB -> out.append(i > 0 ? "-" : "").append(w);
                case CONSTANT -> out.append(i > 0 ? "_" : "").append(w.toUpperCase(Locale.ROOT));
                case CAMEL -> out.append(i == 0 ? w : capitalise(w));
                case PASCAL -> out.append(capitalise(w));
                case TITLE -> out.append(i > 0 ? " " : "").append(capitalise(w));
                case SENTENCE -> out.append(i > 0 ? " " : "").append(i == 0 ? capitalise(w) : w);
                default -> throw new IllegalStateException();
            }
        }
        return out.toString();
    }

    /** The words of text in any case: split at separators and where a lower-case letter or digit meets an upper-case one. */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        for (String part : SEPARATORS.split(text.strip())) {
            StringBuilder word = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                boolean boundary = word.length() > 0 && Character.isUpperCase(c)
                                   && (Character.isLowerCase(part.charAt(i - 1)) || Character.isDigit(part.charAt(i - 1))
                                       || (i + 1 < part.length() && Character.isLowerCase(part.charAt(i + 1))
                                           && Character.isUpperCase(part.charAt(i - 1))));
                if (boundary) {
                    words.add(word.toString());
                    word.setLength(0);
                }
                word.append(c);
            }
            if (word.length() > 0) {
                words.add(word.toString());
            }
        }
        return words;
    }

    private static String capitalise(String w) {
        return w.isEmpty() ? w : w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1);
    }
}
