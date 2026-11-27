package io.joshuasalcedo.schema.predefined;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.engine.Engine;
import io.joshuasalcedo.schema.record.Record;
import io.joshuasalcedo.schema.record.RecordValues;
import io.joshuasalcedo.valueobject.ValueObject;
import io.joshuasalcedo.valueobject.geo.Country;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ValueObjectLibraryTest {

    private static final Schema ALL = CommonObjects.installAll(Schema.builder()).build();

    /** The problems of one value for a value object, as an entity field of that model would get them. */
    private static List<SchemaProblem> check(String modelId, Object value) {
        Model holder = Model.entity("Holder")
                .field(Field.text("id").identity().required())
                .field(Field.model("v", modelId))
                .build();
        Schema schema = CommonObjects.installAll(Schema.builder()).model(holder).build();
        return RecordValues.convert(holder, schema, Map.of("id", "h-1", "v", value), "record").problems();
    }

    private static Object stored(String modelId, Object value) {
        Model holder = Model.entity("Holder")
                .field(Field.text("id").identity().required())
                .field(Field.model("v", modelId))
                .build();
        Schema schema = CommonObjects.installAll(Schema.builder()).model(holder).build();
        RecordValues.Result result = RecordValues.convert(holder, schema, Map.of("id", "h-1", "v", value), "record");
        assertThat(result.problems()).as(modelId + " " + value).isEmpty();
        return result.values().get("v");
    }

    // ------------------------------------------------------------------ the catalog

    @Test
    void theWholeCatalogIsAValidSchema() {
        assertThat(ALL.models()).hasSize(ValueObjectLibrary.all().size() + CommonObjects.all().size()
                + MediaObjects.all().size());
    }

    @Test
    void idsAreUniqueAndEveryModelFieldPointsToAnEarlierModel() {
        List<Model> ordered = new ArrayList<>(ValueObjectLibrary.all());
        ordered.addAll(CommonObjects.all());
        ordered.addAll(MediaObjects.all());
        Set<String> seen = new HashSet<>();
        for (Model model : ordered) {
            assertThat(seen.add(model.id())).as("unique id " + model.id()).isTrue();
            for (Field field : model.fields()) {
                if (field.type() == FieldType.MODEL) {
                    assertThat(seen).as(model.id() + "." + field.name()).contains(field.model());
                }
            }
        }
    }

    @Test
    void everyBusinessValueObjectOfTheLibraryHasAModelAndTechnicalOnesDont() {
        for (ValueObject.Type type : ValueObject.Type.values()) {
            if (!type.type().isRecord()) {
                continue;
            }
            String id = io.joshuasalcedo.schema.Ids.fromDisplayName(type.type().getSimpleName());
            assertThat(ValueObjectLibrary.model(id).isPresent())
                    .as(id).isEqualTo(!ValueObjectLibrary.EXCLUDED.contains(id));
        }
        assertThat(ValueObjectLibrary.model("jwt-token")).isEmpty();
        assertThat(ValueObjectLibrary.model("hashed-password")).isEmpty();
    }

    @Test
    void enumChoicesArePortedInFull() {
        Field country = ValueObjectLibrary.model("address").orElseThrow().field("country").orElseThrow();
        assertThat(country.type()).isEqualTo(FieldType.CHOICE);
        assertThat(country.choices()).containsExactlyElementsOf(Arrays.stream(Country.values()).map(Enum::name).toList());

        Field currency = ValueObjectLibrary.model("money").orElseThrow().field("currency").orElseThrow();
        assertThat(currency.type()).as("Currency is an interface: its enums' constants").isEqualTo(FieldType.CHOICE);
        assertThat(currency.choices()).contains("PHP", "USD", "EUR");
    }

    @Test
    void requiredFollowsTheJavaNullability() {
        assertThat(ValueObjectLibrary.model("email").orElseThrow().field("value").orElseThrow().required()).isTrue();
        Model name = ValueObjectLibrary.model("full-name").orElseThrow();
        assertThat(name.field("firstName").orElseThrow().required()).isTrue();
        assertThat(name.field("middleName").orElseThrow().required()).isFalse();
        Model address = ValueObjectLibrary.model("address").orElseThrow();
        assertThat(address.field("country").orElseThrow().required()).isTrue();
        assertThat(address.field("street").orElseThrow().required()).isFalse();
    }

    @Test
    void descriptionsAndRulesAreCarriedOver() {
        for (Model model : ValueObjectLibrary.all()) {
            assertThat(model.description()).as(model.id()).contains("io.joshuasalcedo.valueobject");
        }
        assertThat(ValueObjectLibrary.model("email").orElseThrow().field("value").orElseThrow().pattern()).isNotBlank();
        assertThat(ValueObjectLibrary.model("port").orElseThrow().field("value").orElseThrow().max())
                .isEqualByComparingTo("65535");
        assertThat(ValueObjectLibrary.model("date-range").orElseThrow().invariants()).isNotEmpty();
    }

    // ------------------------------------------------------------------ values

    @Test
    void realisticValuesConvert() {
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("email", "  Alice@Example.COM ");
        samples.put("phone-number", "+63 917 123 4567");
        samples.put("money", "PHP 1,234.50");
        samples.put("iban", "DE89 3704 0044 0532 0130 00");
        samples.put("link", "https://example.com/a?b=c");
        samples.put("full-name", Map.of("firstName", "Juan", "lastName", "Dela Cruz"));
        samples.put("address", Map.of("street", "1 Ayala Ave", "city", "Makati", "postalCode", "1226",
                "country", "PHILIPPINES"));
        samples.put("date-range", Map.of("startDate", "2026-10-18", "endDate", "2026-10-20"));
        samples.put("coordinates", Map.of("latitude", "14.5547", "longitude", "121.0244"));
        samples.put("port", Map.of("value", 22));
        samples.put("color", Map.of("red", 255, "green", 128, "blue", 0, "alpha", 255));
        samples.put("count", Map.of("value", 3));
        // the kept, hand-made ones
        samples.put("person", Map.of("name", Map.of("firstName", "Mia"), "email", "mia@example.com"));
        samples.put("company", Map.of("name", "Acme", "website", "https://acme.example"));
        samples.put("bank-account", Map.of("accountName", "Acme", "iban", "DE89370400440532013000",
                "currency", Map.of("type", "FiatCurrency", "code", "EUR")));
        samples.put("recurrence", Map.of("frequency", "weekly", "interval", 2));
        samples.put("time-zone", Map.of("id", "Asia/Manila"));
        samples.forEach((id, value) -> assertThat(check(id, value)).as(id + " " + value).isEmpty());
    }

    @Test
    void invalidValuesAreRejected() {
        assertThat(check("email", "not an email")).isNotEmpty();
        assertThat(check("iban", "DE00 0000")).isNotEmpty();
        assertThat(check("money", "lots")).isNotEmpty();
        assertThat(check("recurrence", Map.of("frequency", "HOURLY", "interval", 1))).isNotEmpty();
        assertThat(check("time-zone", Map.of("id", "Mars/Olympus"))).isNotEmpty();
    }

    @Test
    void inputIsCleanedUpByTheJavaClasses() {
        assertThat(stored("email", "  Alice@Example.COM ")).isEqualTo(Map.of("value", "alice@example.com"));
        assertThat(stored("phone-number", "+63 917 123 4567"))
                .isEqualTo(Map.of("countryCode", "63", "nationalNumber", "9171234567"));
        @SuppressWarnings("unchecked")
        Map<String, Object> money = (Map<String, Object>) stored("money", "PHP 1,234.50");
        assertThat(money).containsEntry("currency", "PHP");
        assertThat((BigDecimal) money.get("amount")).isEqualByComparingTo("1234.50");
    }

    @Test
    void limitsAreEnforced() {
        assertThat(check("coordinates", Map.of("latitude", "91", "longitude", "0"))).isNotEmpty();
        assertThat(check("port", Map.of("value", 70000))).isNotEmpty();
        assertThat(check("recurrence", Map.of("frequency", "DAILY", "interval", 0))).isNotEmpty();
    }

    @Test
    void rulesAcrossFieldsAreEnforced() {
        assertThat(check("date-range", Map.of("startDate", "2026-10-20", "endDate", "2026-10-18"))).isNotEmpty();
        assertThat(check("effective-period", Map.of("from", "2026-10-20T00:00:00Z", "through", "2026-10-18T00:00:00Z")))
                .isNotEmpty();
        assertThat(check("bank-account", Map.of("accountName", "Acme"))).isNotEmpty();
    }

    // ------------------------------------------------------------------ installing

    @Test
    void installsIntoAModelServiceOnce() {
        Engine engine = Engine.builder().build();
        assertThat(CommonObjects.installAll(engine.models())).hasSize(ALL.models().size());
        assertThat(CommonObjects.installAll(engine.models())).isEmpty();
    }

    @Test
    void installSkipsModelsThatAlreadyExist() {
        Model mine = Model.valueObject("Email").field(Field.text("address").required()).build();
        Engine engine = Engine.builder().build();
        engine.models().createModel(mine);
        ValueObjectLibrary.install(engine.models());
        assertThat(engine.models().getModel("email")).contains(mine);
    }

    @Test
    void installsIntoASchemaBuilder() {
        Schema schema = ValueObjectLibrary.install(Schema.builder()).build();
        assertThat(schema.models()).hasSameSizeAs(ValueObjectLibrary.all());
    }

    @Test
    void entitiesCanHoldThePredefinedValueObjects() {
        Engine engine = Engine.builder().build();
        CommonObjects.installAll(engine.models());
        engine.models().createModel(Model.entity("Customer")
                .field(Field.text("id").identity().required())
                .field(Field.model("email", "email").required())
                .field(Field.model("phone", "phone-number"))
                .field(Field.model("contact", "person"))
                .build());

        Record customer = engine.create("customer", Map.of("id", "c-1", "email", " Mia@Example.com ",
                "phone", "+63 917 123 4567", "contact", Map.of("name", Map.of("firstName", "Ana"))));
        assertThat(customer.values().get("email")).isEqualTo(Map.of("value", "mia@example.com"));

        assertThatThrownBy(() -> engine.create("customer", Map.of("id", "c-2", "email", "nope")))
                .isInstanceOf(SchemaException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    void anObjectGoesThroughTheRecordsConstructor() {
        assertThat(ValueObjectLibrary.parse("date-range", Map.of("startDate", "2026-10-18", "endDate", "2026-10-20")))
                .containsKeys("startDate", "endDate");
        assertThatThrownBy(() -> ValueObjectLibrary.parse("date-range",
                Map.of("startDate", "2026-10-20", "endDate", "2026-10-18")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHandMadeModelWithALibraryIdIsLeftAlone() {
        Model mine = Model.valueObject("Email").field(Field.text("address").required()).build();
        Model customer = Model.entity("Customer")
                .field(Field.text("id").identity().required())
                .field(Field.model("email", "email"))
                .build();
        Schema schema = Schema.builder().model(mine).model(customer).build();
        assertThat(RecordValues.convert(customer, schema, Map.of("id", "c-1", "email", Map.of("address", "x")),
                "record").problems()).isEmpty();
    }
}
