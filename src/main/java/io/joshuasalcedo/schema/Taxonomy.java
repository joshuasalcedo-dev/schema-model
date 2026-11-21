package io.joshuasalcedo.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A classification: a list, or a tree, of terms that records are put under, such as facility types (station, under
 * it platform and concourse), priorities, or reasons for a stock adjustment. A field takes its terms with
 * {@link Field#category(String, String)}; its value is a term's {@link Term#code() code}, and expressions ask about
 * the tree with {@code IS_A('facility-types', record.facilityType, 'station')}.
 * <pre>{@code
 * Taxonomy.named("Facility types")
 *         .term("station", "Station")
 *         .term("platform", "Platform", "station")
 *         .term("concourse", "Concourse", "station")
 *         .build();
 * }</pre>
 * Terms are business data, so taxonomies are stored and changed at runtime like models (see
 * {@link io.joshuasalcedo.schema.port.in.ModelDesign}); a retired term is made inactive rather than removed, so
 * records that hold it stay readable.
 *
 * @param id          as {@link Model#id()}: lower-case letters, digits and hyphens; derived from the name when not given
 * @param name        the name people see
 * @param description what it classifies; empty when none
 * @param hierarchical whether terms may have a parent (a tree); a flat list when false
 * @param terms       the terms, parents before their children
 */
public record Taxonomy(String id, String name, String description, boolean hierarchical, List<Term> terms) {

    /**
     * One term of a taxonomy.
     *
     * @param code        what records store: unique in the taxonomy, letters, digits, underscores and hyphens, at
     *                    most 64 characters, e.g. {@code metro_station}
     * @param label       what people see: Metro station
     * @param description what it means; empty when none
     * @param aliases     other words people use for it, for search and imports
     * @param parent      the code of the broader term; empty at the top
     * @param active      false for a retired term: records keep it, new values cannot use it
     */
    public record Term(String code, String label, String description, List<String> aliases, String parent,
                       boolean active) {

        /** Replaces null text with empty strings and copies the list. */
        public Term {
            code = Objects.requireNonNullElse(code, "");
            label = Objects.requireNonNullElse(label, "");
            description = Objects.requireNonNullElse(description, "");
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            parent = Objects.requireNonNullElse(parent, "");
        }

        /**
         * An active term.
         *
         * @param code   the code
         * @param label  the label
         * @param parent the parent's code, or empty
         * @return the term
         */
        public static Term of(String code, String label, String parent) {
            return new Term(code, label, "", List.of(), parent, true);
        }
    }

    /** The pattern a term code follows. */
    public static final String CODE = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";

    /** Fills in the ID from the name and copies the list. */
    public Taxonomy {
        name = Objects.requireNonNullElse(name, "");
        id = id == null || id.isEmpty() ? Ids.fromDisplayName(name) : id;
        description = Objects.requireNonNullElse(description, "");
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    /**
     * Starts a hierarchical taxonomy.
     *
     * @param name the name people see; the ID is derived from it ({@code Facility types} is {@code facility-types})
     * @return a builder
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * A builder holding this taxonomy, to make a changed copy.
     *
     * @return a builder
     */
    public Builder toBuilder() {
        Builder b = new Builder(name).id(id).description(description).hierarchical(hierarchical);
        b.terms.addAll(terms);
        return b;
    }

    /**
     * The term with a code.
     *
     * @param code the code
     * @return the term, or empty
     */
    public Optional<Term> term(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return terms.stream().filter(t -> t.code().equals(code)).findFirst();
    }

    /**
     * The term a text names: its code, label or an alias, ignoring case and surrounding spaces. Used to read
     * imports and what people type.
     *
     * @param text a code, label or alias
     * @return the term, or empty
     */
    public Optional<Term> find(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String t = text.strip().toLowerCase(Locale.ROOT);
        return term(text.strip()).or(() -> terms.stream()
                .filter(x -> x.code().toLowerCase(Locale.ROOT).equals(t) || x.label().toLowerCase(Locale.ROOT).equals(t)
                             || x.aliases().stream().anyMatch(a -> a.strip().toLowerCase(Locale.ROOT).equals(t)))
                .findFirst());
    }

    /**
     * The terms at the top (without a parent).
     *
     * @return the top terms, in order
     */
    public List<Term> roots() {
        return terms.stream().filter(t -> t.parent().isEmpty()).toList();
    }

    /**
     * The terms directly under one.
     *
     * @param code the parent's code
     * @return its children, in order
     */
    public List<Term> children(String code) {
        return terms.stream().filter(t -> t.parent().equals(code)).toList();
    }

    /**
     * Every term under one, at any depth.
     *
     * @param code the term's code
     * @return its descendants, depth first
     */
    public List<Term> descendants(String code) {
        List<Term> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collect(code, out, seen);
        return out;
    }

    private void collect(String code, List<Term> out, Set<String> seen) {
        for (Term child : children(code)) {
            if (seen.add(child.code())) {
                out.add(child);
                collect(child.code(), out, seen);
            }
        }
    }

    /**
     * The codes above a term, nearest first: for {@code platform} under {@code station}, {@code [station]}.
     *
     * @param code the term's code
     * @return its ancestors' codes; empty at the top or for an unknown code
     */
    public List<String> ancestors(String code) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Optional<Term> at = term(code);
        while (at.isPresent() && !at.get().parent().isEmpty() && seen.add(at.get().parent())) {
            out.add(at.get().parent());
            at = term(at.get().parent());
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Whether a term is another, or under it at any depth.
     *
     * @param code     the term's code
     * @param ancestor the broader term's code
     * @return true for the term itself and its ancestors
     */
    public boolean isA(String code, String ancestor) {
        return term(code).isPresent() && (code.equals(ancestor) || ancestors(code).contains(ancestor));
    }

    /**
     * Everything wrong with the taxonomy, each with a path to where it is.
     *
     * @return the problems; empty when it is valid
     */
    public List<SchemaProblem> validate() {
        List<SchemaProblem> problems = new ArrayList<>();
        String at = "taxonomies[" + id + "]";
        if (!Ids.isValid(id)) {
            problems.add(new SchemaProblem(at + ".id", at + ".id must match " + Ids.ID + " (got '" + id + "')"));
        }
        if (name.isBlank()) {
            problems.add(new SchemaProblem(at + ".name", at + ".name is required"));
        }
        Map<String, Term> byCode = new LinkedHashMap<>();
        for (int i = 0; i < terms.size(); i++) {
            Term t = terms.get(i);
            String tat = at + ".terms[" + i + "]";
            if (!t.code().matches(CODE)) {
                problems.add(new SchemaProblem(tat + ".code", tat + ".code must match " + CODE + " (got '" + t.code() + "')"));
            } else if (byCode.putIfAbsent(t.code(), t) != null) {
                problems.add(new SchemaProblem(tat + ".code", tat + ".code '" + t.code() + "' is used twice"));
            }
            if (t.label().isBlank()) {
                problems.add(new SchemaProblem(tat + ".label", tat + ".label is required"));
            }
        }
        for (int i = 0; i < terms.size(); i++) {
            Term t = terms.get(i);
            String tat = at + ".terms[" + i + "]";
            if (t.parent().isEmpty()) {
                continue;
            }
            if (!hierarchical) {
                problems.add(new SchemaProblem(tat + ".parent", tat + ".parent: the taxonomy is a flat list"));
            } else if (!byCode.containsKey(t.parent())) {
                problems.add(new SchemaProblem(tat + ".parent", tat + ".parent '" + t.parent() + "' is not a term"));
            } else if (t.parent().equals(t.code()) || ancestors(t.parent()).contains(t.code())) {
                problems.add(new SchemaProblem(tat + ".parent", tat + ".parent: '" + t.code() + "' would be under itself"));
            }
        }
        return problems;
    }

    /** Builds a {@link Taxonomy}; nothing is checked until it is validated. */
    public static final class Builder {
        private final String name;
        private String id = "";
        private String description = "";
        private boolean hierarchical = true;
        private final List<Term> terms = new ArrayList<>();

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
         * Says what it classifies.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Whether terms may have a parent; true by default.
         *
         * @param hierarchical false for a flat list
         * @return this builder
         */
        public Builder hierarchical(boolean hierarchical) {
            this.hierarchical = hierarchical;
            return this;
        }

        /**
         * Adds a term at the top.
         *
         * @param code  the code
         * @param label the label
         * @return this builder
         */
        public Builder term(String code, String label) {
            return term(Term.of(code, label, ""));
        }

        /**
         * Adds a term under another.
         *
         * @param code   the code
         * @param label  the label
         * @param parent the parent's code
         * @return this builder
         */
        public Builder term(String code, String label, String parent) {
            return term(Term.of(code, label, parent));
        }

        /**
         * Adds a term.
         *
         * @param term the term
         * @return this builder
         */
        public Builder term(Term term) {
            terms.add(term);
            return this;
        }

        /**
         * Makes the taxonomy.
         *
         * @return the taxonomy
         */
        public Taxonomy build() {
            return new Taxonomy(id, name, description, hierarchical, terms);
        }
    }
}
