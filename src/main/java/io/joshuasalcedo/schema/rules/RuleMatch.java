package io.joshuasalcedo.schema.rules;

import java.util.Map;
import java.util.Objects;

/**
 * A rule recognised as a filled-in template (see {@link RuleTemplates#recognize}).
 *
 * @param template  the template that generates the rule
 * @param arguments the arguments that generate it, by parameter name, in the form {@link RuleTemplate#create} takes
 */
public record RuleMatch(RuleTemplate template, Map<String, String> arguments) {

    /** Copies the arguments so they cannot change. */
    public RuleMatch {
        Objects.requireNonNull(template, "template");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
