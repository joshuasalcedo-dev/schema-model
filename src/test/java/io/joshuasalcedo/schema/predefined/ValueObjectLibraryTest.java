package io.joshuasalcedo.schema.predefined;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.engine.Engine;
import io.joshuasalcedo.schema.record.Record;
import io.joshuasalcedo.schema.record.RecordValues;
import io.joshuasalcedo.valueobject.ValueObject;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueObjectLibraryTest {

    @Test
    void everyRecordValueObjectOfTheLibraryHasAModel() {
        long records = Arrays.stream(ValueObject.Type.values()).filter(t -> t.type().isRecord()).count();
        assertThat(ValueObjectLibrary.all()).hasSizeGreaterThanOrEqualTo((int) records);
        assertThat(ValueObjectLibrary.model("email")).isPresent();
        assertThat(ValueObjectLibrary.javaClass("phone-number")).contains(io.joshuasalcedo.valueobject.contact.PhoneNumber.class);

        Model address = ValueObjectLibrary.model("address").orElseThrow();
        Field country = address.field("country").orElseThrow();
        assertThat(country.type()).isEqualTo(FieldType.CHOICE);
        assertThat(country.choices()).contains("PHILIPPINES");
    }

    @Test
    void theLibraryIsAValidSchemaInDependencyOrder() {
        Schema schema = ValueObjectLibrary.install(Schema.builder()).build();
        assertThat(schema.models()).hasSameSizeAs(ValueObjectLibrary.all());
    }

    @Test
    void textIsParsedAndCleanedUpByJava() {
        assertThat(ValueObjectLibrary.parse("email", "  Alice@Example.COM ")).containsEntry("value", "alice@example.com");
        assertThat(ValueObjectLibrary.parse("money", "PHP 1,234.50"))
                .containsEntry("amount", new BigDecimal("1234.50"))
                .containsEntry("currency", "PHP");
    }

    @Test
    void anObjectGoesThroughTheRecordsConstructor() {
        Map<String, Object> range = ValueObjectLibrary.parse("date-range",
                Map.of("startDate", "2026-10-18", "endDate", "2026-10-20"));
        assertThat(range).containsKeys("startDate", "endDate");

        assertThatThrownBy(() -> ValueObjectLibrary.parse("date-range",
                Map.of("startDate", "2026-10-20", "endDate", "2026-10-18")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidValuesSayWhy() {
        assertThatThrownBy(() -> ValueObjectLibrary.parse("email", "not an email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("null");
    }

    @Test
    void recordsWithLibraryFieldsAreCheckedByJava() {
        Engine engine = Engine.builder().build();
        ValueObjectLibrary.install(engine.models());
        engine.models().createModel(Model.entity("Customer")
                .field(Field.text("id").identity().required())
                .field(Field.model("email", "email").required())
                .field(Field.model("phone", "phone-number"))
                .build());

        Record customer = engine.create("customer",
                Map.of("id", "c-1", "email", " Mia@Example.com ", "phone", "+63 917 123 4567"));
        assertThat(customer.values().get("email")).isEqualTo(Map.of("value", "mia@example.com"));
        assertThat(customer.values().get("phone")).isEqualTo(Map.of("countryCode", "63", "nationalNumber", "9171234567"));

        assertThatThrownBy(() -> engine.create("customer", Map.of("id", "c-2", "email", "nope")))
                .isInstanceOf(SchemaException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    void aHandMadeModelWithALibraryIdIsLeftAlone() {
        Model mine = Model.valueObject("Email").field(Field.text("address").required()).build();
        Model customer = Model.entity("Customer")
                .field(Field.text("id").identity().required())
                .field(Field.model("email", "email"))
                .build();
        Schema schema = Schema.builder().model(mine).model(customer).build();

        RecordValues.Result result = RecordValues.convert(customer, schema,
                Map.of("id", "c-1", "email", Map.of("address", "anything")), "record");
        assertThat(result.problems()).isEmpty();
    }
}
