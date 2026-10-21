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
import io.joshuasalcedo.schema.predefined.ValueObjects;
import io.joshuasalcedo.schema.record.Record;
import io.joshuasalcedo.schema.record.RecordValues;

class ValueFormatsTest {

    private static final Model CUSTOMER = Model.entity("Customer")
            .field(Field.text("id").identity().required())
            .field(Field.text("name"))
            .build();
    private static final Schema SCHEMA = ValueObjects.install(Schema.builder()).model(CUSTOMER).build();
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
        assertThat(text("money", Map.of("amount", "1234.50",
                                        "currency", Map.of("type", "FiatCurrency", "code", "PHP"))))
                .isEqualTo("PHP 1,234.50");
        assertThat(text("full-name", Map.of("firstName", "Juan", "lastName", "Dela Cruz")))
                .isEqualTo("Juan Dela Cruz");
        assertThat(text("address", Map.of("street", "1 Ayala Ave", "city", "Makati", "postalCode", "1226",
                                          "country", "PHILIPPINES")))
                .isEqualTo("1 Ayala Ave, Makati 1226, Philippines");
        assertThat(text("phone-number", Map.of("countryCode", "+63", "nationalNumber", "917 123 4567")))
                .isEqualTo("+63 9171234567");
        assertThat(text("coordinates", Map.of("latitude", "14.5547", "longitude", "-121.0244")))
                .isEqualTo("14.5547° N, 121.0244° W");
        assertThat(text("weight", Map.of("value", "1.250", "unit", "KILOGRAM"))).isEqualTo("1.25 kg");
        assertThat(text("percentage", Map.of("value", "12.5"))).isEqualTo("12.5%");
        assertThat(text("date-range", Map.of("startDate", "2026-10-18", "endDate", "2026-10-20")))
                .isEqualTo("Oct 18, 2026 – Oct 20, 2026");
        assertThat(text("time", Map.of("hour", 10, "minute", 5, "meridiem", "PM"))).isEqualTo("10:05 PM");
        assertThat(text("recurrence", Map.of("frequency", "WEEKLY", "interval", 2))).isEqualTo("every 2 weeks");
        assertThat(text("recurrence", Map.of("frequency", "DAILY", "interval", 1))).isEqualTo("every day");
        assertThat(text("semantic-version", Map.of("major", 1, "minor", 2, "patch", 3, "preRelease", "rc.1",
                                                   "buildMetadata", "build.5"))).isEqualTo("1.2.3-rc.1+build.5");
        assertThat(text("color", Map.of("red", 255, "green", 128))).isEqualTo("#FF8000");
        assertThat(text("color", Map.of("red", 255, "alpha", 128))).isEqualTo("#FF000080");
        assertThat(text("ssh-connection", Map.of("host", Map.of("value", "10.0.0.5"), "port", Map.of("value", 22),
                                                 "username", "deploy"))).isEqualTo("deploy@10.0.0.5:22");
        assertThat(text("duration-nano", Map.of("nanos", 1_500_000_000L))).isEqualTo("1.5 s");
        assertThat(text("duration-nano", Map.of("nanos", 3_725_000_000_000L))).isEqualTo("1h 2m");
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
