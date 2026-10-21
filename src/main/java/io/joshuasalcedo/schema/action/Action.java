package io.joshuasalcedo.schema.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.joshuasalcedo.schema.Field;

/**
 * Business logic as data: what input an action takes, what must be true, and what it changes. One engine runs every
 * action; define a new one and it is live, with no new code or endpoint.
 * <pre>{@code
 * Action.named("CloseAccount")
 *         .on("customer")
 *         .input(Field.text("reason").required())
 *         .require("record.status == 'active'", "only active accounts can be closed")
 *         .set("status", "'closed'")
 *         .set("closedReason", "input.reason")
 *         .emit("customer.closed")
 *         .build();
 * }</pre>
 * <h2>Running</h2>
 * The engine converts the input against {@link #input()}, checks every {@link Rule}, runs the {@link Step}s in
 * order, checks the resulting record against its model and saves it, then publishes the events. It is all or
 * nothing: if any part fails, nothing is saved and nothing is published.
 * <h2>Expressions</h2>
 * Rules and set steps are CEL (Common Expression Language). They see:
 * <ul>
 *   <li>{@code record}: the record's values so far (empty for a creating action), by field name</li>
 *   <li>{@code input}: the action's input, by field name</li>
 *   <li>{@code now}: the time the action started, as an ISO-8601 string</li>
 * </ul>
 * DECIMAL values are doubles there; DATE and TIMESTAMP values are ISO-8601 strings, which compare correctly as
 * text. A field with no value is absent, so test optional fields with {@code has(record.field)} before reading them.
 * An action is immutable; whether it is valid is decided by {@link ActionValidator} when it is defined.
 *
 * @param name        unique, e.g. {@code CloseAccount}: starts with a letter, then letters, digits, {@code _ . -}
 * @param description what it is for, shown to people choosing an action
 * @param model       the ID of the entity model it works on
 * @param target      on an existing record, or creating a new one
 * @param input       the fields the caller passes, checked like a value object's fields
 * @param rules       all must be true before any step runs
 * @param steps       run in order; the record is checked against its model and saved after the last one
 */
public record Action(String name, String description, String model, Target target, List<Field> input,
                     List<Rule> rules, List<Step> steps) {

    /** What an action works on. */
    public enum Target {
        /** Runs on a record that exists; the caller passes its ID. */
        EXISTING,
        /** Creates the record; {@code record} starts empty and the ID comes from the identity fields it sets. */
        NEW
    }

    /** Replaces null parts with empty ones and copies the lists so the action cannot change later. */
    public Action {
        name = Objects.requireNonNullElse(name, "");
        description = Objects.requireNonNullElse(description, "");
        model = Objects.requireNonNullElse(model, "");
        target = Objects.requireNonNullElse(target, Target.EXISTING);
        input = input == null ? List.of() : List.copyOf(input);
        rules = rules == null ? List.of() : List.copyOf(rules);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * Starts an action.
     *
     * @param name the action's unique name
     * @return a builder for the action
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * The events its {@link Step.Emit} steps publish, besides the {@code {model}.created} or
     * {@code {model}.updated} event every change publishes.
     *
     * @return the event names, in step order
     */
    public List<String> events() {
        return steps.stream().filter(s -> s instanceof Step.Emit).map(s -> ((Step.Emit) s).event()).toList();
    }

    /**
     * Builds an {@link Action} step by step. Rules and steps are kept in the order they are added; nothing is
     * checked until the action is defined on the engine.
     */
    public static final class Builder {
        private final String name;
        private String description = "";
        private String model = "";
        private Target target = Target.EXISTING;
        private final List<Field> input = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Sets what the action is for.
         *
         * @param description free text
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Makes the action run on an existing record.
         *
         * @param model the entity model's ID
         * @return this builder
         */
        public Builder on(String model) {
            this.model = model;
            this.target = Target.EXISTING;
            return this;
        }

        /**
         * Makes the action create a record.
         *
         * @param model the entity model's ID
         * @return this builder
         */
        public Builder creates(String model) {
            this.model = model;
            this.target = Target.NEW;
            return this;
        }

        /**
         * Adds an input field the caller passes.
         *
         * @param field the field; identity fields are not allowed in input
         * @return this builder
         */
        public Builder input(Field field) {
            input.add(field);
            return this;
        }

        /**
         * Adds an input field, building it first.
         *
         * @param field the field's builder
         * @return this builder
         */
        public Builder input(Field.Builder field) {
            return input(field.build());
        }

        /**
         * Adds a rule with the default message.
         *
         * @param expression CEL that must be true
         * @return this builder
         */
        public Builder require(String expression) {
            return require(expression, null);
        }

        /**
         * Adds a rule.
         *
         * @param expression CEL that must be true
         * @param message    what the caller is told when it is false
         * @return this builder
         */
        public Builder require(String expression, String message) {
            rules.add(new Rule(expression, message));
            return this;
        }

        /**
         * Adds a {@link Step.Set} step.
         *
         * @param field      the field to set
         * @param expression CEL giving its new value; {@code null} clears it
         * @return this builder
         */
        public Builder set(String field, String expression) {
            steps.add(new Step.Set(field, expression));
            return this;
        }

        /**
         * Adds a {@link Step.Call} step.
         *
         * @param handler the name of a handler registered on the engine
         * @return this builder
         */
        public Builder call(String handler) {
            steps.add(new Step.Call(handler));
            return this;
        }

        /**
         * Adds a {@link Step.Emit} step.
         *
         * @param event the event name
         * @return this builder
         */
        public Builder emit(String event) {
            steps.add(new Step.Emit(event));
            return this;
        }

        /**
         * Makes the action.
         *
         * @return an immutable action with everything added so far
         */
        public Action build() {
            return new Action(name, description, model, target, input, rules, steps);
        }
    }
}
