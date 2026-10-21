package io.joshuasalcedo.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An entity or a value object, the way the business talks about it: a name, a kind and a list of fields.
 * <pre>{@code
 * Model customer = Model.entity("Customer")
 *         .field(Field.text("id").identity().required())
 *         .field(Field.text("email").required().pattern(".+@.+"))
 *         .field(Field.model("billingAddress", "address"))
 *         .build();
 * }</pre>
 * A model is immutable. Absent text parts are stored as empty strings and absent lists as empty lists, never null.
 * Whether a model is valid is decided by {@link SchemaValidator}, against the {@link Schema} it belongs to.
 *
 * @param id              how other models and fields refer to this one, {@code [a-z0-9-]{3,63}}; when not given it
 *                        is made from the display name ({@code EmailAddress} becomes {@code email-address})
 * @param displayName     as written in code, e.g. {@code Customer}
 * @param description     extra notes beyond the term's definition; empty when none
 * @param kind            entity or value object
 * @param term            optional glossary term this model stands for; empty when none
 * @param fields          in display order
 * @param relatedArticles references to documents that explain the model, in any form the application uses
 * @param invariants      rules across the fields that every value must keep, checked whenever one is stored
 */
public record Model(String id, String displayName, String description, ModelKind kind, String term,
                    List<Field> fields, List<String> relatedArticles, List<Invariant> invariants) {

    /** Fills in the ID from the display name when it is missing, and copies the lists so they cannot change. */
    public Model {
        displayName = Objects.requireNonNullElse(displayName, "");
        id = id == null || id.isEmpty() ? Ids.fromDisplayName(displayName) : id;
        description = Objects.requireNonNullElse(description, "");
        term = Objects.requireNonNullElse(term, "");
        fields = fields == null ? List.of() : List.copyOf(fields);
        relatedArticles = relatedArticles == null ? List.of() : List.copyOf(relatedArticles);
        invariants = invariants == null ? List.of() : List.copyOf(invariants);
    }

    /**
     * A model without invariants: the form before invariants existed, kept so existing callers still compile.
     *
     * @param id              the model ID, or empty to derive it from the display name
     * @param displayName     the name as written in code
     * @param description     notes, or empty
     * @param kind            entity or value object
     * @param term            the glossary term, or empty
     * @param fields          the fields
     * @param relatedArticles references to explaining documents
     */
    public Model(String id, String displayName, String description, ModelKind kind, String term,
                 List<Field> fields, List<String> relatedArticles) {
        this(id, displayName, description, kind, term, fields, relatedArticles, List.of());
    }

    /**
     * Starts an {@link ModelKind#ENTITY}: something with an identity, such as a customer or an order.
     *
     * @param displayName the name as written in code, e.g. {@code Customer}
     * @return a builder for the model
     */
    public static Builder entity(String displayName) {
        return new Builder(displayName, ModelKind.ENTITY);
    }

    /**
     * Starts a {@link ModelKind#VALUE_OBJECT}: something defined only by its values, such as an address or an amount
     * of money.
     *
     * @param displayName the name as written in code, e.g. {@code Address}
     * @return a builder for the model
     */
    public static Builder valueObject(String displayName) {
        return new Builder(displayName, ModelKind.VALUE_OBJECT);
    }

    /**
     * Starts a model of the given kind.
     *
     * @param displayName the name as written in code
     * @param kind        entity or value object
     * @return a builder for the model
     */
    public static Builder of(String displayName, ModelKind kind) {
        return new Builder(displayName, kind);
    }

    /**
     * Looks up a field by its exact name.
     *
     * @param name the field name
     * @return the field, or empty if the model has none by that name
     */
    public Optional<Field> field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    /**
     * The fields that make up the identity, in field order; their values form a record's ID.
     *
     * @return the identity fields; empty for value objects and for entities without any
     */
    public List<Field> identityFields() {
        return fields.stream().filter(Field::identity).toList();
    }

    /**
     * Whether this is an entity.
     *
     * @return true for {@link ModelKind#ENTITY}
     */
    public boolean isEntity() {
        return kind == ModelKind.ENTITY;
    }

    /**
     * Whether this is a value object.
     *
     * @return true for {@link ModelKind#VALUE_OBJECT}
     */
    public boolean isValueObject() {
        return kind == ModelKind.VALUE_OBJECT;
    }

    /**
     * A builder holding this model's values, to make a changed copy (the ID is kept).
     *
     * @return a builder starting from this model
     */
    public Builder toBuilder() {
        Builder b = new Builder(displayName, kind);
        b.id = id;
        b.description = description;
        b.term = term;
        b.fields.addAll(fields);
        b.relatedArticles.addAll(relatedArticles);
        b.invariants.addAll(invariants);
        return b;
    }

    /**
     * Builds a {@link Model} step by step. Every option returns the builder so calls can be chained; nothing is
     * checked until the model is validated.
     */
    public static final class Builder {
        private String id = "";
        private String displayName;
        private ModelKind kind;
        private String description = "";
        private String term = "";
        private final List<Field> fields = new ArrayList<>();
        private final List<String> relatedArticles = new ArrayList<>();
        private final List<Invariant> invariants = new ArrayList<>();

        private Builder(String displayName, ModelKind kind) {
            this.displayName = displayName;
            this.kind = kind;
        }

        /**
         * Sets the ID instead of deriving it from the display name.
         *
         * @param id {@code [a-z0-9-]{3,63}}
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Changes the display name.
         *
         * @param displayName the name as written in code
         * @return this builder
         */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Changes the kind.
         *
         * @param kind entity or value object
         * @return this builder
         */
        public Builder kind(ModelKind kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Sets notes beyond the term's definition.
         *
         * @param description free text
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Points the model to the glossary term it stands for.
         *
         * @param term the term's name
         * @return this builder
         */
        public Builder term(String term) {
            this.term = term;
            return this;
        }

        /**
         * Appends a field.
         *
         * @param field the field
         * @return this builder
         */
        public Builder field(Field field) {
            fields.add(field);
            return this;
        }

        /**
         * Appends a field, building it first, so {@code .field(Field.text("name"))} needs no {@code build()}.
         *
         * @param field the field's builder
         * @return this builder
         */
        public Builder field(Field.Builder field) {
            return field(field.build());
        }

        /**
         * Replaces all fields.
         *
         * @param fields the new fields, in order
         * @return this builder
         */
        public Builder fields(List<Field> fields) {
            this.fields.clear();
            this.fields.addAll(fields);
            return this;
        }

        /**
         * Adds a reference to a document that explains the model.
         *
         * @param article the document's reference
         * @return this builder
         */
        public Builder relatedArticle(String article) {
            relatedArticles.add(article);
            return this;
        }

        /**
         * Adds a rule across the fields, with the default message.
         *
         * @param expression CEL over {@code value}, {@code today} and {@code now} that must be true
         * @return this builder
         */
        public Builder invariant(String expression) {
            return invariant(expression, null);
        }

        /**
         * Adds a rule across the fields.
         *
         * @param expression CEL over {@code value}, {@code today} and {@code now} that must be true
         * @param message    what the caller is told when it is false
         * @return this builder
         */
        public Builder invariant(String expression, String message) {
            invariants.add(new Invariant(expression, message));
            return this;
        }

        /**
         * Makes the model.
         *
         * @return an immutable model with the options set so far
         */
        public Model build() {
            return new Model(id, displayName, description, kind, term, fields, relatedArticles, invariants);
        }
    }
}
