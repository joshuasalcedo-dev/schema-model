package io.joshuasalcedo.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.joshuasalcedo.schema.application.ModelService;
import io.joshuasalcedo.schema.adapter.InMemoryModelRepository;
import io.joshuasalcedo.schema.port.out.ModelEvents;

class SchemaTest {

    private static final Model ADDRESS = Model.valueObject("Address")
            .term("address")
            .field(Field.text("street").required())
            .field(Field.text("postcode").pattern("[0-9]{4,5}"))
            .build();

    private static final Model CUSTOMER = Model.entity("Customer")
            .relatedArticle("articles/customers")
            .field(Field.text("id").identity().required())
            .field(Field.text("email").required().term("email-address"))
            .field(Field.choice("status", "active", "closed"))
            .field(Field.model("billingAddress", "address"))
            .build();

    @Test
    void buildsAValidSchema() {
        Schema schema = Schema.builder().model(ADDRESS).model(CUSTOMER).build();

        assertThat(schema.models()).extracting(Model::id).containsExactly("address", "customer");
        assertThat(schema.usedBy("address")).containsExactly("customer");
        assertThat(schema.usedTerms()).containsExactly("address", "email-address");
        assertThat(schema.analyze()).isEmpty();
    }

    @Test
    void idsComeFromDisplayNames() {
        assertThat(Ids.fromDisplayName("EmailAddress")).isEqualTo("email-address");
        assertThat(Ids.fromDisplayName("Order line")).isEqualTo("order-line");
        assertThat(Ids.fromDisplayName("HTTPRequest")).isEqualTo("http-request");
    }

    @Test
    void reportsEveryProblemAtOnce() {
        Model bad = Model.valueObject("")
                .field(Field.text("1st"))
                .field(Field.text("name"))
                .field(Field.text("Name"))
                .field(Field.integer("age").pattern("[0-9]+"))
                .field(Field.text("code").pattern("("))
                .field(Field.choice("status", " "))
                .field(Field.text("id").identity())
                .field(Field.of("x", null))
                .field(Field.model("other", "missing-model"))
                .build();

        List<SchemaProblem> problems = SchemaValidator.validate(bad, Schema.empty());

        assertThat(problems).extracting(SchemaProblem::path).containsExactly(
                "model.display_name",
                "model.id",
                "model.fields[0].name",
                "model.fields[2].name",
                "model.fields[3].pattern",
                "model.fields[4].pattern",
                "model.fields[5].choices",
                "model.fields[6].identity",
                "model.fields[7].type",
                "model.fields[8].model");
    }

    @Test
    void checksTermsAgainstTheVocabulary() {
        Schema schema = Schema.builder().vocabulary(Vocabulary.of("address")).model(ADDRESS).build();

        assertThat(SchemaValidator.validate(CUSTOMER, schema))
                .extracting(SchemaProblem::message)
                .containsExactly("model.fields[1].term 'email-address' is not in the vocabulary");
    }

    @Test
    void valueObjectsCannotEmbedThemselves() {
        Model a = Model.valueObject("A").field(Field.model("b", "bbb")).build();
        Model b = Model.valueObject("Bbb").id("bbb").field(Field.model("a", "a")).build();

        assertThatThrownBy(() -> Schema.builder().model(a).model(b).build())
                .isInstanceOf(SchemaException.class)
                .hasMessageContaining("value objects cannot embed themselves: A → Bbb → A");
    }

    @Test
    void entitiesMayReferToThemselves() {
        Model employee = Model.entity("Employee")
                .field(Field.text("id").identity())
                .field(Field.model("manager", "employee"))
                .build();

        assertThat(Schema.builder().model(employee).build().validate()).isEmpty();
    }

    @Test
    void analysisFindsUndocumentedEntitiesWithoutIdentity() {
        Model order = Model.entity("Order").field(Field.decimal("total")).build();

        assertThat(SchemaAnalysis.analyze(order)).extracting(Finding::check).containsExactly(
                Finding.Check.ENTITY_WITHOUT_IDENTITY, Finding.Check.MODEL_UNDOCUMENTED);
    }

    @Test
    void serviceRunsTheUseCasesThroughThePorts() {
        List<String> seen = new ArrayList<>();
        ModelEvents events = new ModelEvents() {
            @Override
            public void created(Model model) {
                seen.add("created " + model.id());
            }

            @Override
            public void updated(Model before, Model after) {
                seen.add("updated " + after.id());
            }

            @Override
            public void deleted(Model model) {
                seen.add("deleted " + model.id());
            }
        };
        ModelService design = new ModelService(new InMemoryModelRepository(), Vocabulary::open, events);

        assertThatThrownBy(() -> design.createModel(CUSTOMER))
                .isInstanceOfSatisfying(SchemaException.class,
                                        e -> assertThat(e.reason()).isEqualTo(SchemaException.Reason.INVALID));

        design.createModel(ADDRESS);
        design.createModel(CUSTOMER);
        assertThatThrownBy(() -> design.createModel(ADDRESS))
                .isInstanceOfSatisfying(SchemaException.class,
                                        e -> assertThat(e.reason()).isEqualTo(SchemaException.Reason.ALREADY_EXISTS));
        assertThatThrownBy(() -> design.deleteModel("address"))
                .isInstanceOfSatisfying(SchemaException.class,
                                        e -> assertThat(e.reason()).isEqualTo(SchemaException.Reason.IN_USE));

        design.updateModel(CUSTOMER.toBuilder().fields(List.of(Field.text("id").identity().build())).build());
        design.deleteModel("address");
        assertThatThrownBy(() -> design.deleteModel("address"))
                .isInstanceOfSatisfying(SchemaException.class,
                                        e -> assertThat(e.reason()).isEqualTo(SchemaException.Reason.NOT_FOUND));

        assertThat(design.listModels()).extracting(Model::id).containsExactly("customer");
        assertThat(seen).containsExactly("created address", "created customer", "updated customer",
                                         "deleted address");
    }
}
