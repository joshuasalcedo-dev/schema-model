package io.joshuasalcedo.schema.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.SchemaValidator;

/**
 * The rules an action must follow: a valid name; an entity model that exists; input fields as valid as a value
 * object's; rules that compile to true/false; set steps on real fields (never identity fields of an existing
 * record, which would change its ID) with compiling expressions; handlers that are registered; and event names.
 */
public final class ActionValidator {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");
    private static final Pattern EVENT = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    private ActionValidator() {
    }

    /**
     * Checks an action before it is defined.
     *
     * @param action      the action
     * @param schema      the models it may work on and refer to
     * @param handlers    the names of the handlers registered on the engine
     * @param expressions compiles the CEL expressions
     * @return every problem, with paths like {@code action.steps[2].field}; empty when valid
     */
    public static List<SchemaProblem> validate(Action action, Schema schema, Set<String> handlers,
                                               Expressions expressions) {
        List<SchemaProblem> problems = new ArrayList<>();
        if (!NAME.matcher(action.name()).matches()) {
            add(problems, "action.name", "must start with a letter and use letters, digits, '_', '.' and '-'");
        }
        Model model = schema.model(action.model()).orElse(null);
        if (model == null) {
            add(problems, "action.model", "model '" + action.model() + "' was not found");
        } else if (!model.isEntity()) {
            add(problems, "action.model", "'" + action.model() + "' is a value object; actions work on entities");
        }

        for (SchemaProblem p : SchemaValidator.validate(inputModel(action), schema)) {
            if (!p.path().equals("model.display_name") && !p.path().equals("model.id")) {
                String path = p.path().replaceFirst("^model\\.fields", "action.input");
                problems.add(new SchemaProblem(path, p.message().replaceFirst("^model\\.fields", "action.input")));
            }
        }

        for (int i = 0; i < action.rules().size(); i++) {
            String at = "action.rules[" + i + "]";
            Rule rule = action.rules().get(i);
            if (rule.expression().isBlank()) {
                add(problems, at, "is empty");
            } else {
                expressions.check(rule.expression(), true).ifPresent(e -> add(problems, at, e));
            }
        }

        Set<String> setFields = new HashSet<>();
        for (int i = 0; i < action.steps().size(); i++) {
            String at = "action.steps[" + i + "]";
            switch (action.steps().get(i)) {
                case Step.Set set -> {
                    Field field = model == null ? null : model.field(set.field()).orElse(null);
                    if (model != null && field == null) {
                        add(problems, at + ".field", "'" + set.field() + "' is not a field of " + model.displayName());
                    } else if (field != null && field.identity() && action.target() == Action.Target.EXISTING) {
                        add(problems, at + ".field", "'" + set.field() + "' is part of the identity; it cannot change");
                    }
                    if (!setFields.add(set.field())) {
                        add(problems, at + ".field", "'" + set.field() + "' is set twice");
                    }
                    if (set.expression().isBlank()) {
                        add(problems, at + ".expression", "is empty");
                    } else {
                        expressions.check(set.expression(), false).ifPresent(e -> add(problems, at + ".expression", e));
                    }
                }
                case Step.Call call -> {
                    if (!handlers.contains(call.handler())) {
                        add(problems, at + ".handler", "no handler is registered as '" + call.handler() + "'");
                    }
                }
                case Step.Emit emit -> {
                    if (!EVENT.matcher(emit.event()).matches()) {
                        add(problems, at + ".event", "must use letters, digits, '_', '.' and '-'");
                    }
                }
            }
        }
        return problems;
    }

    /**
     * An action's input as a value object of its own (ID {@code action-input}), so input is checked and converted
     * exactly like a model's values.
     *
     * @param action the action
     * @return a value object whose fields are the action's input fields
     */
    public static Model inputModel(Action action) {
        return Model.valueObject(action.name().isBlank() ? "Input" : action.name() + "Input")
                .id("action-input")
                .fields(action.input())
                .build();
    }

    private static void add(List<SchemaProblem> problems, String path, String message) {
        problems.add(new SchemaProblem(path, path + " " + message));
    }
}
