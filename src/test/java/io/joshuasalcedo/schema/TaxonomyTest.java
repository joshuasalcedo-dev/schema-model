package io.joshuasalcedo.schema;

import io.joshuasalcedo.schema.action.Expressions;
import io.joshuasalcedo.schema.engine.Engine;
import io.joshuasalcedo.schema.record.RecordValues;
import io.joshuasalcedo.schema.text.RecordLookup;
import io.joshuasalcedo.schema.text.ValueFormats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxonomyTest {

    private static final Taxonomy FACILITY_TYPES = Taxonomy.named("Facility types")
            .description("What kind of place a facility is.")
            .term("station", "Station")
            .term("platform", "Platform", "station")
            .term("concourse", "Concourse", "station")
            .term(new Taxonomy.Term("platform_edge_door", "Platform edge door", "", List.of("PSD", "PED"), "platform", true))
            .term("depot", "Depot")
            .build();

    @Test
    void walksTheTree() {
        assertThat(FACILITY_TYPES.id()).isEqualTo("facility-types");
        assertThat(FACILITY_TYPES.validate()).isEmpty();
        assertThat(FACILITY_TYPES.roots()).extracting(Taxonomy.Term::code).containsExactly("station", "depot");
        assertThat(FACILITY_TYPES.children("station")).extracting(Taxonomy.Term::code).containsExactly("platform", "concourse");
        assertThat(FACILITY_TYPES.descendants("station")).extracting(Taxonomy.Term::code)
                .containsExactly("platform", "platform_edge_door", "concourse");
        assertThat(FACILITY_TYPES.ancestors("platform_edge_door")).containsExactly("platform", "station");
        assertThat(FACILITY_TYPES.isA("platform_edge_door", "station")).isTrue();
        assertThat(FACILITY_TYPES.isA("platform", "platform")).isTrue();
        assertThat(FACILITY_TYPES.isA("depot", "station")).isFalse();
        assertThat(FACILITY_TYPES.find(" psd ")).map(Taxonomy.Term::code).contains("platform_edge_door");
        assertThat(FACILITY_TYPES.find("Concourse")).map(Taxonomy.Term::code).contains("concourse");
    }

    @Test
    void reportsEveryMistake() {
        Taxonomy bad = Taxonomy.named("Bad")
                .term("a", "A", "b")
                .term("b", "B", "a")
                .term("a", "Again")
                .term("c", "", "missing")
                .term("has space", "X")
                .build();
        assertThat(bad.validate()).extracting(SchemaProblem::message)
                .anyMatch(m -> m.contains("would be under itself"))
                .anyMatch(m -> m.contains("used twice"))
                .anyMatch(m -> m.contains("'missing' is not a term"))
                .anyMatch(m -> m.contains("label is required"))
                .anyMatch(m -> m.contains("code must match"));
        Taxonomy flat = Taxonomy.named("Flat").hierarchical(false).term("a", "A").term("b", "B", "a").build();
        assertThat(flat.validate()).extracting(SchemaProblem::message).anyMatch(m -> m.contains("flat list"));
    }

    @Test
    void aCategoryFieldTakesOnlyTermsAndStoresTheCode() {
        Model facility = Model.entity("Facility")
                .field(Field.text("code").identity().required())
                .field(Field.category("facilityType", "facility-types").required())
                .build();
        Schema schema = Schema.builder().taxonomy(FACILITY_TYPES).model(facility).build();
        RecordValues.Result ok = RecordValues.convert(facility, schema, Map.of("code", "F1", "facilityType", "PSD"), "x");
        assertThat(ok.valid()).isTrue();
        assertThat(ok.values()).containsEntry("facilityType", "platform_edge_door");
        RecordValues.Result bad = RecordValues.convert(facility, schema, Map.of("code", "F2", "facilityType", "airport"), "x");
        assertThat(bad.valid()).isFalse();
        assertThat(bad.problems()).extracting(SchemaProblem::message).anyMatch(m -> m.contains("not a term of Facility types"));
        assertThat(ValueFormats.defaults(schema, RecordLookup.none()).text(facility.field("facilityType").orElseThrow(), "platform"))
                .isEqualTo("Platform");
    }

    @Test
    void aFieldMustNameATaxonomyThatExists() {
        Model facility = Model.entity("Facility")
                .field(Field.text("code").identity().required())
                .field(Field.category("facilityType", "nope"))
                .field(Field.integer("floors").taxonomy("facility-types"))
                .build();
        assertThat(Schema.builder().taxonomy(FACILITY_TYPES).model(facility).buildUnchecked().validate())
                .extracting(SchemaProblem::message)
                .anyMatch(m -> m.contains("taxonomy 'nope' was not found"))
                .anyMatch(m -> m.contains("taxonomy is only for TEXT fields"));
    }

    @Test
    void expressionsAskAboutTheTree() {
        Schema schema = Schema.builder().taxonomy(FACILITY_TYPES).build();
        Expressions expressions = new Expressions();
        Map<String, ?> vars = Map.of("record", Map.of("facilityType", "platform_edge_door"));
        assertThat(expressions.eval("IS_A('facility-types', record.facilityType, 'station')", vars, schema)).isEqualTo(true);
        assertThat(expressions.eval("IS_A('facility-types', record.facilityType, 'depot')", vars, schema)).isEqualTo(false);
        assertThat(expressions.eval("PARENT_OF('facility-types', record.facilityType)", vars, schema)).isEqualTo("platform");
        assertThat(expressions.eval("ANCESTORS('facility-types', record.facilityType)", vars, schema))
                .isEqualTo(List.of("platform", "station"));
        assertThat(expressions.eval("LABEL_OF('facility-types', record.facilityType)", vars, schema)).isEqualTo("Platform edge door");
        assertThatThrownBy(() -> expressions.eval("IS_A('facility-types', 'x', 'y')", Map.of()))
                .hasMessageContaining("need the schema");
    }

    @Test
    void theEngineKeepsTaxonomiesAndChecksRecordsAndInvariants() {
        Engine engine = Engine.builder().build();
        engine.models().saveTaxonomy(FACILITY_TYPES);
        engine.models().createModel(Model.entity("Facility")
                .field(Field.text("code").identity().required())
                .field(Field.category("facilityType", "facility-types").required())
                .field(Field.text("line"))
                .invariant("!IS_A('facility-types', value.facilityType, 'station') || has(value.line)",
                           "a station or anything in one needs its line")
                .build());
        engine.create("facility", Map.of("code", "DEP1", "facilityType", "depot"));
        engine.create("facility", Map.of("code", "MSH", "facilityType", "Platform", "line", "Red"));
        assertThatThrownBy(() -> engine.create("facility", Map.of("code", "X", "facilityType", "concourse")))
                .hasMessageContaining("needs its line");
        assertThatThrownBy(() -> engine.models().deleteTaxonomy("facility-types")).hasMessageContaining("is used by facility");
        assertThat(engine.models().listTaxonomies()).hasSize(1);
        assertThatThrownBy(() -> engine.models().saveTaxonomy(Taxonomy.named("Broken").term("a", "A", "zz").build()))
                .isInstanceOf(SchemaException.class);
    }
}
