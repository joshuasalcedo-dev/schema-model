package io.joshuasalcedo.schema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The states something goes through and the moves allowed between them: a facility is Active, Under maintenance,
 * Inactive or Decommissioned; from Active it may go to Under maintenance, but never back from Decommissioned.
 * <pre>{@code
 * Lifecycle.named("Facility lifecycle")
 *         .state(State.of("active", "Active", Phase.ACTIVE).asInitial().withColor("#16A34A").withIcon("mdi:check-circle"))
 *         .state(State.of("under_maintenance", "Under maintenance", Phase.ON_HOLD).withColor("#F59E0B").withIcon("mdi:wrench"))
 *         .state(State.of("decommissioned", "Decommissioned", Phase.RETIRED).asTerminal().withColor("#6B7280"))
 *         .transition(Transition.of("start_maintenance", "Start maintenance", "under_maintenance", "active"))
 *         .transition(Transition.of("finish_maintenance", "Finish maintenance", "active", "under_maintenance"))
 *         .transition(Transition.of("decommission", "Decommission", "decommissioned", "active", "under_maintenance"))
 *         .build();
 * }</pre>
 * People name their own states, colour them and give them icons; each state maps to a fixed {@link Phase}, which is what
 * code reads ("may this record still change?" is "is its phase RETIRED?"), so renaming or adding states never breaks it.
 * Lifecycles are stored and changed at runtime like models (see {@link io.joshuasalcedo.schema.port.in.ModelDesign}).
 *
 * @param id          as a model's ID; derived from the name when not given
 * @param name        the name people see
 * @param description what it is for; empty when none
 * @param states      the states, in the order they are shown
 * @param transitions the allowed moves, in the order they are offered
 */
public record Lifecycle(String id, String name, String description, List<State> states, List<Transition> transitions) {

    /** The fixed meaning of a state, which code reads; people name the states themselves. */
    public enum Phase {
        /** Being prepared: a draft, a request not yet accepted. */
        NEW,
        /** In use, in force, being worked on. */
        ACTIVE,
        /** Paused for now: on hold, under maintenance, suspended. */
        ON_HOLD,
        /** Not in use, but may come back. */
        INACTIVE,
        /** Finished as intended: done, completed, closed. */
        DONE,
        /** Stopped before finishing: cancelled, rejected. */
        CANCELLED,
        /** Gone for good: decommissioned, scrapped, archived. Nothing about it changes any more. */
        RETIRED
    }

    /** A colour as #RRGGBB. */
    public static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    /** An icon name, with an optional set prefix: wrench, mdi:wrench. */
    public static final Pattern ICON = Pattern.compile("[a-z0-9][a-z0-9-]*(?::[a-z0-9][a-z0-9-]*)?");
    /** The pattern state and transition codes follow. */
    public static final String CODE = "[a-z][a-z0-9_]{0,63}";

    /**
     * One state.
     *
     * @param code        what records store: under_maintenance
     * @param label       what people see: Under maintenance
     * @param description what it means; empty when none
     * @param phase       its fixed meaning
     * @param initial     where a new record starts; exactly one state is initial
     * @param terminal    nothing leaves it
     * @param color       its colour as #RRGGBB; empty for the screen's default
     * @param icon        its icon (mdi:wrench); empty for none
     */
    public record State(String code, String label, String description, Phase phase, boolean initial, boolean terminal,
                        String color, String icon) {

        /** Replaces null text with empty strings. */
        public State {
            code = Objects.requireNonNullElse(code, "");
            label = Objects.requireNonNullElse(label, "");
            description = Objects.requireNonNullElse(description, "");
            color = Objects.requireNonNullElse(color, "");
            icon = Objects.requireNonNullElse(icon, "");
        }

        /**
         * A state that is neither initial nor terminal, with no colour or icon.
         *
         * @param code  the code
         * @param label the label
         * @param phase its phase
         * @return the state
         */
        public static State of(String code, String label, Phase phase) {
            return new State(code, label, "", phase, false, false, "", "");
        }

        /** @return this state as the one new records start in */
        public State asInitial() {
            return new State(code, label, description, phase, true, terminal, color, icon);
        }

        /** @return this state with nothing leaving it */
        public State asTerminal() {
            return new State(code, label, description, phase, initial, true, color, icon);
        }

        /**
         * @param color #RRGGBB, or empty
         * @return this state in that colour
         */
        public State withColor(String color) {
            return new State(code, label, description, phase, initial, terminal, color, icon);
        }

        /**
         * @param icon an icon name (mdi:wrench), or empty
         * @return this state with that icon
         */
        public State withIcon(String icon) {
            return new State(code, label, description, phase, initial, terminal, color, icon);
        }

        /**
         * @param label the new label
         * @return this state with another label
         */
        public State withLabel(String label) {
            return new State(code, label, description, phase, initial, terminal, color, icon);
        }

        /**
         * @param description what it means
         * @return this state with that description
         */
        public State withDescription(String description) {
            return new State(code, label, description, phase, initial, terminal, color, icon);
        }
    }

    /**
     * One allowed move.
     *
     * @param code      its code: start_maintenance
     * @param label     what its button says: Start maintenance
     * @param from      the states it may start from; empty = any state that is not terminal
     * @param to        the state it goes to
     * @param condition CEL that must be true over {@code record} (and {@code input}), or empty: {@code size(record.openWorkOrders) == 0}
     * @param required  field names that must have a value before the move
     * @param event     the event name the move emits, or empty for none: facility.decommissioned
     * @param color     its button's colour as #RRGGBB; empty for the default
     * @param icon      its button's icon; empty for none
     */
    public record Transition(String code, String label, List<String> from, String to, String condition, List<String> required,
                             String event, String color, String icon) {

        /** Replaces null text with empty strings and copies the lists. */
        public Transition {
            code = Objects.requireNonNullElse(code, "");
            label = Objects.requireNonNullElse(label, "");
            from = from == null ? List.of() : List.copyOf(from);
            to = Objects.requireNonNullElse(to, "");
            condition = Objects.requireNonNullElse(condition, "");
            required = required == null ? List.of() : List.copyOf(required);
            event = Objects.requireNonNullElse(event, "");
            color = Objects.requireNonNullElse(color, "");
            icon = Objects.requireNonNullElse(icon, "");
        }

        /**
         * A move with no condition, required fields, event, colour or icon.
         *
         * @param code  the code
         * @param label the button's label
         * @param to    the state it goes to
         * @param from  the states it starts from; none = any state that is not terminal
         * @return the transition
         */
        public static Transition of(String code, String label, String to, String... from) {
            return new Transition(code, label, List.of(from), to, "", List.of(), "", "", "");
        }

        /**
         * @param condition CEL that must be true
         * @return this move with that condition
         */
        public Transition when(String condition) {
            return new Transition(code, label, from, to, condition, required, event, color, icon);
        }

        /**
         * @param fields field names that must have a value first
         * @return this move with those fields required
         */
        public Transition requires(String... fields) {
            return new Transition(code, label, from, to, condition, List.of(fields), event, color, icon);
        }

        /**
         * @param event the event name to emit
         * @return this move emitting it
         */
        public Transition emits(String event) {
            return new Transition(code, label, from, to, condition, required, event, color, icon);
        }

        /**
         * @param color #RRGGBB, or empty
         * @return this move's button in that colour
         */
        public Transition withColor(String color) {
            return new Transition(code, label, from, to, condition, required, event, color, icon);
        }

        /**
         * @param icon an icon name, or empty
         * @return this move's button with that icon
         */
        public Transition withIcon(String icon) {
            return new Transition(code, label, from, to, condition, required, event, color, icon);
        }

        /**
         * @param label the new label
         * @return this move with another label
         */
        public Transition withLabel(String label) {
            return new Transition(code, label, from, to, condition, required, event, color, icon);
        }

        /**
         * Whether it may start from a state.
         *
         * @param state the state's code
         * @param lifecycle the lifecycle it belongs to (for "any state that is not terminal")
         * @return true when it may
         */
        public boolean startsFrom(String state, Lifecycle lifecycle) {
            return from.isEmpty() ? lifecycle.state(state).map(s -> !s.terminal()).orElse(false) : from.contains(state);
        }
    }

    /** Fills in the ID from the name and copies the lists. */
    public Lifecycle {
        name = Objects.requireNonNullElse(name, "");
        id = id == null || id.isEmpty() ? Ids.fromDisplayName(name) : id;
        description = Objects.requireNonNullElse(description, "");
        states = states == null ? List.of() : List.copyOf(states);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }

    /**
     * Starts a lifecycle.
     *
     * @param name the name people see; the ID is derived from it
     * @return a builder
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    // ---------- reading ----------

    /**
     * @param code a state's code
     * @return the state, or empty
     */
    public Optional<State> state(String code) {
        return states.stream().filter(s -> s.code().equals(code)).findFirst();
    }

    /** @return the state new records start in */
    public State initialState() {
        return states.stream().filter(State::initial).findFirst()
                .orElseThrow(() -> new IllegalStateException("lifecycle '" + id + "' has no initial state"));
    }

    /**
     * @param code a transition's code
     * @return the transition, or empty
     */
    public Optional<Transition> transition(String code) {
        return transitions.stream().filter(t -> t.code().equals(code)).findFirst();
    }

    /**
     * The moves offered from a state, in order: what a screen shows as buttons.
     *
     * @param state the current state's code
     * @return the transitions that may start there
     */
    public List<Transition> transitionsFrom(String state) {
        return transitions.stream().filter(t -> t.startsFrom(state, this)).toList();
    }

    /**
     * Whether some move goes from one state to another (conditions aside).
     *
     * @param from the current state's code
     * @param to   the wanted state's code
     * @return true when a transition allows it
     */
    public boolean canMove(String from, String to) {
        return transitionsFrom(from).stream().anyMatch(t -> t.to().equals(to));
    }

    /**
     * The phase of a state.
     *
     * @param code the state's code
     * @return its phase
     * @throws IllegalArgumentException for an unknown state
     */
    public Phase phaseOf(String code) {
        return state(code).map(State::phase)
                .orElseThrow(() -> new IllegalArgumentException("'" + code + "' is not a state of " + name));
    }

    // ---------- changing (each gives a new lifecycle) ----------

    /**
     * This lifecycle with a state added, or replaced when its code exists.
     *
     * @param state the state
     * @return a new lifecycle
     */
    public Lifecycle withState(State state) {
        List<State> next = new ArrayList<>(states);
        int i = indexOfState(state.code());
        if (i < 0) {
            next.add(state);
        } else {
            next.set(i, state);
        }
        return new Lifecycle(id, name, description, next, transitions);
    }

    /**
     * This lifecycle with a transition added, or replaced when its code exists.
     *
     * @param transition the transition
     * @return a new lifecycle
     */
    public Lifecycle withTransition(Transition transition) {
        List<Transition> next = new ArrayList<>(transitions);
        int i = -1;
        for (int k = 0; k < next.size(); k++) {
            if (next.get(k).code().equals(transition.code())) {
                i = k;
            }
        }
        if (i < 0) {
            next.add(transition);
        } else {
            next.set(i, transition);
        }
        return new Lifecycle(id, name, description, states, next);
    }

    /**
     * This lifecycle with how a state looks changed: its label, colour and icon. What it means (its phase) and where it
     * may go stay as they are, so records in it are not affected.
     *
     * @param code  the state's code
     * @param label the new label; null to keep it
     * @param color the new colour (#RRGGBB, or empty for none); null to keep it
     * @param icon  the new icon (or empty for none); null to keep it
     * @return a new lifecycle
     * @throws IllegalArgumentException for an unknown state
     */
    public Lifecycle restyle(String code, String label, String color, String icon) {
        State s = state(code).orElseThrow(() -> new IllegalArgumentException("'" + code + "' is not a state of " + name));
        State next = s.withLabel(label == null ? s.label() : label).withColor(color == null ? s.color() : color)
                .withIcon(icon == null ? s.icon() : icon);
        return withState(next);
    }

    private int indexOfState(String code) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).code().equals(code)) {
                return i;
            }
        }
        return -1;
    }

    // ---------- checking ----------

    /**
     * Everything wrong with the lifecycle, each with a path: codes, exactly one initial state, transitions that point at
     * real states, colours and icons, and states nothing can reach.
     *
     * @return the problems; empty when it is valid
     */
    public List<SchemaProblem> validate() {
        List<SchemaProblem> problems = new ArrayList<>();
        String at = "lifecycles[" + id + "]";
        if (!Ids.isValid(id)) {
            problems.add(new SchemaProblem(at + ".id", at + ".id must match " + Ids.ID + " (got '" + id + "')"));
        }
        if (name.isBlank()) {
            problems.add(new SchemaProblem(at + ".name", at + ".name is required"));
        }
        if (states.isEmpty()) {
            problems.add(new SchemaProblem(at + ".states", at + ".states: give at least one"));
            return problems;
        }
        Map<String, State> byCode = new LinkedHashMap<>();
        for (int i = 0; i < states.size(); i++) {
            State s = states.get(i);
            String sat = at + ".states[" + i + "]";
            if (!s.code().matches(CODE)) {
                problems.add(new SchemaProblem(sat + ".code", sat + ".code must match " + CODE + " (got '" + s.code() + "')"));
            } else if (byCode.putIfAbsent(s.code(), s) != null) {
                problems.add(new SchemaProblem(sat + ".code", sat + ".code '" + s.code() + "' is used twice"));
            }
            if (s.label().isBlank()) {
                problems.add(new SchemaProblem(sat + ".label", sat + ".label is required"));
            }
            if (s.phase() == null) {
                problems.add(new SchemaProblem(sat + ".phase", sat + ".phase is required"));
            }
            look(s.color(), s.icon(), sat, problems);
        }
        long initials = states.stream().filter(State::initial).count();
        if (initials != 1) {
            problems.add(new SchemaProblem(at + ".states", at + ".states: exactly one must be initial (" + initials + " are)"));
        }
        Set<String> codes = new LinkedHashSet<>();
        for (int i = 0; i < transitions.size(); i++) {
            Transition t = transitions.get(i);
            String tat = at + ".transitions[" + i + "]";
            if (!t.code().matches(CODE)) {
                problems.add(new SchemaProblem(tat + ".code", tat + ".code must match " + CODE + " (got '" + t.code() + "')"));
            } else if (!codes.add(t.code())) {
                problems.add(new SchemaProblem(tat + ".code", tat + ".code '" + t.code() + "' is used twice"));
            }
            if (t.label().isBlank()) {
                problems.add(new SchemaProblem(tat + ".label", tat + ".label is required"));
            }
            if (!byCode.containsKey(t.to())) {
                problems.add(new SchemaProblem(tat + ".to", tat + ".to '" + t.to() + "' is not a state"));
            }
            for (String f : t.from()) {
                if (!byCode.containsKey(f)) {
                    problems.add(new SchemaProblem(tat + ".from", tat + ".from '" + f + "' is not a state"));
                } else if (byCode.get(f).terminal()) {
                    problems.add(new SchemaProblem(tat + ".from", tat + ".from '" + f + "' is terminal: nothing leaves it"));
                }
            }
            if (!t.condition().isBlank()) {
                io.joshuasalcedo.schema.action.Expressions.shared().check(t.condition(), true)
                        .ifPresent(e -> problems.add(new SchemaProblem(tat + ".condition", tat + ".condition: " + e)));
            }
            look(t.color(), t.icon(), tat, problems);
        }
        if (initials == 1 && problems.isEmpty()) {
            Set<String> reached = new LinkedHashSet<>();
            Deque<String> todo = new ArrayDeque<>(List.of(initialState().code()));
            while (!todo.isEmpty()) {
                String s = todo.pop();
                if (reached.add(s)) {
                    transitionsFrom(s).forEach(t -> todo.push(t.to()));
                }
            }
            for (State s : states) {
                if (!reached.contains(s.code())) {
                    problems.add(new SchemaProblem(at + ".states", at + ".states: '" + s.code() + "' cannot be reached from '"
                                                                   + initialState().code() + "'"));
                }
            }
        }
        return problems;
    }

    private static void look(String color, String icon, String at, List<SchemaProblem> problems) {
        if (!color.isEmpty() && !COLOR.matcher(color).matches()) {
            problems.add(new SchemaProblem(at + ".color", at + ".color must be #RRGGBB (got '" + color + "')"));
        }
        if (!icon.isEmpty() && !ICON.matcher(icon).matches()) {
            problems.add(new SchemaProblem(at + ".icon", at + ".icon must be a name such as mdi:wrench (got '" + icon + "')"));
        }
    }

    /** Builds a {@link Lifecycle}; nothing is checked until it is validated. */
    public static final class Builder {
        private final String name;
        private String id = "";
        private String description = "";
        private final List<State> states = new ArrayList<>();
        private final List<Transition> transitions = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param id the ID instead of one derived from the name
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * @param description what it is for
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * @param state a state, in display order
         * @return this builder
         */
        public Builder state(State state) {
            states.add(state);
            return this;
        }

        /**
         * @param transition an allowed move, in the order offered
         * @return this builder
         */
        public Builder transition(Transition transition) {
            transitions.add(transition);
            return this;
        }

        /** @return the lifecycle */
        public Lifecycle build() {
            return new Lifecycle(id, name, description, states, transitions);
        }
    }
}
