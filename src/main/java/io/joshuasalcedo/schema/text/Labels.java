package io.joshuasalcedo.schema.text;

import java.util.Locale;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;

/** Turns schema names into text for people: field names, enum constants and model names. */
public final class Labels {

    private Labels() {
    }

    /**
     * A field's label: {@code firstName} becomes "First name", {@code stateOrProvince} "State or province",
     * {@code addressLine2} "Address line 2".
     *
     * @param field the field
     * @return the label
     */
    public static String of(Field field) {
        return words(field.name());
    }

    /**
     * A model's label: {@code EmailAddress} becomes "Email address".
     *
     * @param model the model
     * @return the label
     */
    public static String of(Model model) {
        return words(model.displayName());
    }

    /**
     * Splits camelCase, PascalCase, snake_case and kebab-case into a sentence-case phrase.
     *
     * @param name the name
     * @return the words, first one capitalised
     */
    public static String words(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String spaced = name.strip()
                .replaceAll("([a-z])([A-Z0-9])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([0-9])([A-Za-z])", "$1 $2")
                .replaceAll("[_\\-]+", " ")
                .toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * An enum constant or choice for people: {@code UNITED_KINGDOM} becomes "United Kingdom", {@code NAUTICAL_MILE}
     * "Nautical Mile". Mixed-case choices ({@code FiatCurrency}) and short codes ({@code USD}, {@code AM}) are kept
     * as they are.
     *
     * @param choice the choice
     * @return the label
     */
    public static String choice(String choice) {
        if (choice == null || choice.isEmpty() || !choice.equals(choice.toUpperCase(Locale.ROOT))
            || (choice.length() <= 4 && !choice.contains("_"))) {
            return choice;
        }
        StringBuilder out = new StringBuilder();
        for (String word : choice.split("_+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
