package io.joshuasalcedo.schema.action;

import io.joshuasalcedo.schema.Ids;
import io.joshuasalcedo.schema.SchemaProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A decision as a table, the way people write rules in a spreadsheet: inputs across, one rule per row, the answer on
 * the right. The first row whose cells all match gives the outputs (or, by the hit policy, the only matching row,
 * or every matching row).
 * <pre>
 * | priority    | contractType    || responseTime | penalty |
 * | 'emergency' | -               || 'PT4H'       | 1.0     |
 * | 'high'      | 'comprehensive' || 'PT8H'       | 0.5     |
 * | 'high'      | -               || 'PT24H'      | 0.5     |
 * | -           | -               || 'P5D'        | 0       |
 * </pre>
 * <pre>{@code
 * DecisionTable.named("SLA")
 *         .input("priority").input("contractType")
 *         .output("responseTime").output("penalty")
 *         .row("'emergency'", "-", "'PT4H'", "1.0")
 *         .row("'high'", "'comprehensive'", "'PT8H'", "0.5")
 *         .row("'high'", "-", "'PT24H'", "0.5")
 *         .row("-", "-", "'P5D'", "0")
 *         .build();
 * }</pre>
 * An expression calls it with {@code DECIDE('sla', record)}: the second argument is the table's {@code facts}, and
 * each input is read from it ({@code facts.priority}) unless the input gives its own expression
 * ({@code facts.contract.type}). A cell is one of:
 * <ul>
 *   <li>{@code -} (or empty): any value</li>
 *   <li>a value, {@code 'emergency'} or {@code 5}: equal to it</li>
 *   <li>a comparison, {@code > 1000}, {@code <= 5}, {@code != 'closed'}</li>
 *   <li>a range, {@code [1..10]} (both ends included)</li>
 *   <li>a list, {@code in ['a', 'b']}</li>
 *   <li>any CEL that uses {@code v}, the input's value: {@code v > 0 && v % 2 == 0}, {@code IS_EMPTY(v)}</li>
 * </ul>
 * An output cell is CEL over {@code facts}: a value ({@code 'PT4H'}) or a calculation ({@code facts.value * 0.01}).
 * Tables are stored and changed at runtime like models (see {@link io.joshuasalcedo.schema.port.in.ModelDesign}).
 *
 * @param id          as a model's ID; derived from the name when not given
 * @param name        the name people see
 * @param description what it decides; empty when none
 * @param hitPolicy   what happens when several rows match
 * @param inputs      the columns read, in order
 * @param outputs     the columns given, in order
 * @param rows        the rules, in order
 */
public record DecisionTable(String id, String name, String description, HitPolicy hitPolicy, List<Input> inputs,
                            List<Output> outputs, List<Row> rows) {

    /** What happens when more than one row matches. */
    public enum HitPolicy {
        /** The first matching row, in order (a default row goes last). */
        FIRST,
        /** At most one row may match; two matching rows are an error in the table. */
        UNIQUE,
        /** Every matching row: the result is a list of outputs. */
        COLLECT
    }

    /**
     * A column the table reads.
     *
     * @param name       the column's name, e.g. {@code priority}
     * @param expression CEL over {@code facts} giving its value; empty means {@code facts.<name>}
     */
    public record Input(String name, String expression) {

        /** Replaces null with empty text. */
        public Input {
            name = Objects.requireNonNullElse(name, "");
            expression = Objects.requireNonNullElse(expression, "");
        }

        /**
         * The expression that gives the value.
         *
         * @return the expression, or {@code facts.<name>} (empty when the facts do not have it)
         */
        public String effectiveExpression() {
            return expression.isBlank() ? "has(facts." + name + ") ? facts." + name + " : null" : expression;
        }
    }

    /**
     * A column the table gives.
     *
     * @param name the output's name, e.g. {@code responseTime}
     */
    public record Output(String name) {
    }

    /**
     * One rule.
     *
     * @param when        one cell per input
     * @param then        one CEL expression per output
     * @param description why the rule is there; empty when none
     */
    public record Row(List<String> when, List<String> then, String description) {

        /** Copies the lists. */
        public Row {
            when = when == null ? List.of() : List.copyOf(when);
            then = then == null ? List.of() : List.copyOf(then);
            description = Objects.requireNonNullElse(description, "");
        }
    }

    private static final Pattern RANGE = Pattern.compile("\\[\\s*(.+?)\\s*\\.\\.\\s*(.+?)\\s*]");
    private static final Pattern USES_V = Pattern.compile("(?<![\\w.'\"])v(?![\\w'\"])");
    private static final Pattern NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");

    /** Fills in the ID and copies the lists. */
    public DecisionTable {
        name = Objects.requireNonNullElse(name, "");
        id = id == null || id.isEmpty() ? Ids.fromDisplayName(name) : id;
        description = Objects.requireNonNullElse(description, "");
        hitPolicy = Objects.requireNonNullElse(hitPolicy, HitPolicy.FIRST);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /**
     * Starts a table with the {@link HitPolicy#FIRST} policy.
     *
     * @param name the name; the ID is derived from it
     * @return a builder
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * The CEL test a cell stands for, over {@code v} (the input's value).
     *
     * @param cell the cell as written
     * @return the boolean CEL expression
     */
    public static String cellTest(String cell) {
        String c = cell == null ? "" : cell.strip();
        if (c.isEmpty() || c.equals("-") || c.equals("*")) {
            return "true";
        }
        Matcher range = RANGE.matcher(c);
        if (range.matches()) {
            return "v >= " + range.group(1) + " && v <= " + range.group(2);
        }
        for (String op : new String[]{"<=", ">=", "!=", "==", "<", ">"}) {
            if (c.startsWith(op)) {
                return "v " + c;
            }
        }
        if (c.startsWith("in ") || c.startsWith("in[")) {
            return "v " + c;
        }
        if (USES_V.matcher(c).find()) {
            return c;
        }
        return "v == " + c;
    }

    /**
     * Decides: evaluates the rows against the facts.
     *
     * @param facts       the values the inputs read (a record's values, already in CEL types)
     * @param expressions the expression runner
     * @return for FIRST and UNIQUE the matching row's outputs by name (empty when no row matches); for COLLECT a
     *         list of them
     * @throws IllegalArgumentException when UNIQUE finds two matching rows, or a cell cannot be evaluated
     */
    public Object decide(Map<String, ?> facts, Expressions expressions) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("facts", facts == null ? Map.of() : facts);
        List<Object> values = new ArrayList<>();
        for (Input input : inputs) {
            values.add(expressions.eval(input.effectiveExpression(), vars));
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Integer> hitRows = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (matches(row, values, vars, expressions)) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (int o = 0; o < outputs.size(); o++) {
                    out.put(outputs.get(o).name(), expressions.eval(row.then().get(o), vars));
                }
                if (hitPolicy == HitPolicy.FIRST) {
                    return out;
                }
                hits.add(out);
                hitRows.add(r + 1);
            }
        }
        return switch (hitPolicy) {
            case FIRST -> Map.of();
            case UNIQUE -> {
                if (hits.size() > 1) {
                    throw new IllegalArgumentException("decision '" + id + "': rows " + hitRows
                                                       + " all match, but only one may (hit policy UNIQUE)");
                }
                yield hits.isEmpty() ? Map.of() : hits.getFirst();
            }
            case COLLECT -> List.copyOf(hits);
        };
    }

    private boolean matches(Row row, List<Object> values, Map<String, Object> vars, Expressions expressions) {
        for (int i = 0; i < inputs.size(); i++) {
            String test = cellTest(row.when().get(i));
            if (test.equals("true")) {
                continue;
            }
            Map<String, Object> cellVars = new LinkedHashMap<>(vars);
            Object value = values.get(i);
            boolean empty = ExcelFunctions.isEmpty(value);
            cellVars.put("v", value == null ? Expressions.celNull() : value);
            try {
                if (!Boolean.TRUE.equals(expressions.eval(test, cellVars))) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                if (empty) {
                    return false; // an empty value does not match a comparison ("> 1000" of nothing)
                }
                throw new IllegalArgumentException("decision '" + id + "', input " + inputs.get(i).name() + ": " + e.getMessage(), e);
            }
        }
        return true;
    }

    /**
     * Everything wrong with the table, each with a path to where it is: names, row sizes, and cells or outputs that
     * do not compile.
     *
     * @return the problems; empty when it is valid
     */
    public List<SchemaProblem> validate() {
        List<SchemaProblem> problems = new ArrayList<>();
        String at = "decisions[" + id + "]";
        Expressions check = Expressions.shared();
        if (!Ids.isValid(id)) {
            problems.add(new SchemaProblem(at + ".id", at + ".id must match " + Ids.ID + " (got '" + id + "')"));
        }
        if (name.isBlank()) {
            problems.add(new SchemaProblem(at + ".name", at + ".name is required"));
        }
        if (outputs.isEmpty()) {
            problems.add(new SchemaProblem(at + ".outputs", at + ".outputs: give at least one"));
        }
        checkNames(inputs.stream().map(Input::name).toList(), at + ".inputs", problems);
        checkNames(outputs.stream().map(Output::name).toList(), at + ".outputs", problems);
        for (int i = 0; i < inputs.size(); i++) {
            int index = i;
            check.check(inputs.get(i).effectiveExpression(), false).ifPresent(e -> problems.add(
                    new SchemaProblem(at + ".inputs[" + index + "]", at + ".inputs[" + index + "]: " + e)));
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            String rat = at + ".rows[" + r + "]";
            if (row.when().size() != inputs.size()) {
                problems.add(new SchemaProblem(rat + ".when", rat + ".when has " + row.when().size() + " cells for "
                                                              + inputs.size() + " inputs"));
                continue;
            }
            if (row.then().size() != outputs.size()) {
                problems.add(new SchemaProblem(rat + ".then", rat + ".then has " + row.then().size() + " cells for "
                                                              + outputs.size() + " outputs"));
                continue;
            }
            for (int c = 0; c < row.when().size(); c++) {
                String cat = rat + ".when[" + c + "]";
                String cell = row.when().get(c);
                check.check(cellTest(cell), true)
                        .ifPresent(e -> problems.add(new SchemaProblem(cat, cat + " '" + cell + "': " + e)));
            }
            for (int c = 0; c < row.then().size(); c++) {
                String cat = rat + ".then[" + c + "]";
                String cell = row.then().get(c);
                if (cell == null || cell.isBlank()) {
                    problems.add(new SchemaProblem(cat, cat + " is empty; write null for no value"));
                    continue;
                }
                check.check(cell, false).ifPresent(e -> problems.add(new SchemaProblem(cat, cat + " '" + cell + "': " + e)));
            }
        }
        return problems;
    }

    private static void checkNames(List<String> names, String at, List<SchemaProblem> problems) {
        Set<String> dupes = names.stream().filter(n -> names.stream().filter(n::equalsIgnoreCase).count() > 1)
                .collect(Collectors.toSet());
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (!NAME.matcher(n).matches()) {
                problems.add(new SchemaProblem(at + "[" + i + "]", at + "[" + i + "] '" + n + "' is not a name (a letter, then letters, digits, underscores)"));
            } else if (dupes.contains(n)) {
                problems.add(new SchemaProblem(at + "[" + i + "]", at + "[" + i + "] '" + n + "' is used twice"));
            }
        }
    }

    /** Builds a {@link DecisionTable}; nothing is checked until it is validated. */
    public static final class Builder {
        private final String name;
        private String id = "";
        private String description = "";
        private HitPolicy hitPolicy = HitPolicy.FIRST;
        private final List<Input> inputs = new ArrayList<>();
        private final List<Output> outputs = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Sets the ID instead of deriving it from the name.
         *
         * @param id the ID
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Says what it decides.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the hit policy; {@link HitPolicy#FIRST} by default.
         *
         * @param hitPolicy the policy
         * @return this builder
         */
        public Builder hitPolicy(HitPolicy hitPolicy) {
            this.hitPolicy = hitPolicy;
            return this;
        }

        /**
         * Adds an input read as {@code facts.<name>}.
         *
         * @param name the name
         * @return this builder
         */
        public Builder input(String name) {
            return input(name, "");
        }

        /**
         * Adds an input with its own expression.
         *
         * @param name       the name
         * @param expression CEL over {@code facts}
         * @return this builder
         */
        public Builder input(String name, String expression) {
            inputs.add(new Input(name, expression));
            return this;
        }

        /**
         * Adds an output.
         *
         * @param name the name
         * @return this builder
         */
        public Builder output(String name) {
            outputs.add(new Output(name));
            return this;
        }

        /**
         * Adds a row: first one cell per input, then one per output, in column order.
         *
         * @param cells the cells
         * @return this builder
         * @throws IllegalStateException if the number of cells is not inputs + outputs
         */
        public Builder row(String... cells) {
            if (cells.length != inputs.size() + outputs.size()) {
                throw new IllegalStateException("a row needs " + (inputs.size() + outputs.size()) + " cells ("
                                                + inputs.size() + " inputs, " + outputs.size() + " outputs), not "
                                                + cells.length + "; add the inputs and outputs first");
            }
            List<String> all = Arrays.asList(cells);
            rows.add(new Row(all.subList(0, inputs.size()), all.subList(inputs.size(), cells.length), ""));
            return this;
        }

        /**
         * Adds a row.
         *
         * @param row the row
         * @return this builder
         */
        public Builder row(Row row) {
            rows.add(row);
            return this;
        }

        /**
         * Makes the table.
         *
         * @return the table
         */
        public DecisionTable build() {
            return new DecisionTable(id, name, description, hitPolicy, inputs, outputs, rows);
        }
    }
}
