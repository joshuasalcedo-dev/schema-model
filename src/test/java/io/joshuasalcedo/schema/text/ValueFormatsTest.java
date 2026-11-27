package io.joshuasalcedo.schema.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.predefined.ValueObjectLibrary;
import io.joshuasalcedo.valueobject.ValueObject;
import io.joshuasalcedo.schema.record.Record;
import io.joshuasalcedo.schema.record.RecordValues;

class ValueFormatsTest {

    private static final Model CUSTOMER = Model.entity("Customer")
            .field(Field.text("id").identity().required())
            .field(Field.text("name"))
            .build();
    private static final Schema SCHEMA = ValueObjectLibrary.install(Schema.builder()).model(CUSTOMER).build();
    private static final RecordLookup LOOKUP = model -> model.equals("customer")
            ? List.of(new Record("customer", "c-1", Map.of("id", "c-1", "name", "Alice"), 1)) : List.of();
    private static final ValueFormats FORMATS = ValueFormats.defaults(SCHEMA, LOOKUP, Locale.US, ZoneOffset.UTC);

    /** Converts like a stored record would hold it, then formats. */
    private static String text(String model, Map<String, ?> raw) {
        RecordValues.Result r = RecordValues.convert(SCHEMA.requireModel(model), SCHEMA, raw, "x");
        assertThat(r.problems()).as(model).isEmpty();
        return FORMATS.text(model, r.values());
    }

    @Test
    void predefinedValueObjectsLookLikeTheirJavaToString() {
        // given as text, stored as the Java value object cleaned it up, printed as it prints itself
        String[][] samples = {
                {"email", "EMAIL", "  Alice@Example.COM "},
                {"phone-number", "PHONE_NUMBER", "+63 917 123 4567"},
                {"money", "MONEY", "PHP 1,234.50"},
                {"iban", "IBAN", "DE89 3704 0044 0532 0130 00"},
                {"link", "LINK", "https://example.com/a"},
        };
        for (String[] sample : samples) {
            String expected = ValueObject.Type.valueOf(sample[1]).parse(sample[2]).toString();
            assertThat(text(sample[0], ValueObjectLibrary.parse(sample[0], sample[2]))).as(sample[0]).isEqualTo(expected);
        }
        assertThat(text("date-range", Map.of("startDate", "2026-10-18", "endDate", "2026-10-20")))
                .isEqualTo(ValueObjectLibrary.text("date-range",
                        Map.of("startDate", LocalDate.of(2026, 10, 18), "endDate", LocalDate.of(2026, 10, 20))));
    }

    @Test
    void fieldsHaveDefaults() {
        assertThat(FORMATS.text(Field.decimal("x").build(), new BigDecimal("1234.5"))).isEqualTo("1,234.5");
        assertThat(FORMATS.text(Field.integer("x").build(), 1234567L)).isEqualTo("1,234,567");
        assertThat(FORMATS.text(Field.bool("x").build(), true)).isEqualTo("Yes");
        assertThat(FORMATS.text(Field.choice("x", "UNITED_KINGDOM").build(), "UNITED_KINGDOM"))
                .isEqualTo("United Kingdom");
        assertThat(FORMATS.text(Field.date("x").build(), LocalDate.of(2026, 10, 18))).isEqualTo("Oct 18, 2026");
        assertThat(FORMATS.text(Field.timestamp("x").build(), Instant.parse("2026-10-18T14:00:00Z")))
                .startsWith("Oct 18, 2026");
        assertThat(FORMATS.text(Field.text("tags").repeated().build(), List.of("a", "b"))).isEqualTo("a, b");
        assertThat(FORMATS.text(Field.model("owner", "customer").build(), "c-1")).isEqualTo("Alice (c-1)");
        assertThat(FORMATS.text(Field.model("owner", "customer").build(), "c-9")).isEqualTo("c-9");
        assertThat(FORMATS.text(Field.text("x").build(), null)).isEmpty();
    }

    @Test
    void labelsReadLikeSentences() {
        assertThat(Labels.words("stateOrProvince")).isEqualTo("State or province");
        assertThat(Labels.words("addressLine2")).isEqualTo("Address line 2");
        assertThat(Labels.words("HTTPRequest")).isEqualTo("Http request");
        assertThat(Labels.choice("NAUTICAL_MILE")).isEqualTo("Nautical Mile");
        assertThat(Labels.choice("USD")).isEqualTo("USD");
        assertThat(Labels.choice("FiatCurrency")).isEqualTo("FiatCurrency");
    }

    @Test
    void customFormatsReplaceDefaults() {
        ValueFormats formats = ValueFormats.defaults(SCHEMA, RecordLookup.none(), Locale.US, ZoneOffset.UTC)
                .register("money", (v, f) -> "custom");
        assertThat(formats.text("money", Map.of("amount", BigDecimal.ONE))).isEqualTo("custom");
    }
}
