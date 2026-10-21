package io.joshuasalcedo.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Quality checks for models, for any schema. The findings are
 * advice; the schema is valid either way.
 */
public final class SchemaAnalysis {

    private SchemaAnalysis() {
    }

    /**
     * Checks every model of the schema.
     *
     * @param schema the schema
     * @return the findings, grouped by model in schema order; empty when there is nothing to improve
     */
    public static List<Finding> analyze(Schema schema) {
        List<Finding> findings = new ArrayList<>();
        for (Model model : schema.models()) {
            findings.addAll(analyze(model));
        }
        return findings;
    }

    /**
     * Checks one model.
     *
     * @param model the model
     * @return the findings; empty when there is nothing to improve
     */
    public static List<Finding> analyze(Model model) {
        List<Finding> findings = new ArrayList<>();
        if (model.kind() == ModelKind.ENTITY && model.fields().stream().noneMatch(Field::identity)) {
            findings.add(new Finding(Finding.Check.ENTITY_WITHOUT_IDENTITY, model.id(), model.displayName(),
                                     "Entity with no identity field"));
        }
        if (model.term().isEmpty() && model.relatedArticles().isEmpty()) {
            findings.add(new Finding(Finding.Check.MODEL_UNDOCUMENTED, model.id(), model.displayName(),
                                     "No glossary term and no related articles explain it"));
        }
        return findings;
    }
}
