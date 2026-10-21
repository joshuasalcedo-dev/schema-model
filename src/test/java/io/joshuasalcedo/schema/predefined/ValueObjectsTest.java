package io.joshuasalcedo.schema.predefined;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.application.ModelService;
import io.joshuasalcedo.schema.record.RecordValues;

class ValueObjectsTest {

    private static final Schema SCHEMA = Schema.builder().models(ValueObjects.all()).build();

    @Test
    void theWholeCatalogIsAValidSchema() {
        assertThatCode(() -> Schema.builder().models(ValueObjects.all()).build()).doesNotThrowAnyException();
        assertThat(SCHEMA.validate()).isEmpty();
        assertThat(ValueObjects.all()).hasSize(39).allMatch(Model::isValueObject);
    }

    @Test
    void idsAreUniqueAndEveryModelFieldPointsIntoTheCatalogToAnEarlierModel() {
        Set<String> seen = new HashSet<>();
        for (Model model : ValueObjects.all()) {
            for (Field field : model.fields()) {
                if (field.type() == FieldType.MODEL) {
                    assertThat(seen).as(model.id() + "." + field.name()).contains(field.model());
                }
            }
            assertThat(seen.add(model.id())).as("duplicate " + model.id()).isTrue();
        }
        assertThat(seen).contains("email-address", "phone-number", "semantic-version", "ipv4-address",
                                  "crypto-currency-address", "date-time-with-zone", "money", "address");
    }

    @Test
    void everyAreaIsInAll() {
        int total = 0;
        for (ValueObjects.Area area : ValueObjects.Area.values()) {
            assertThat(ValueObjects.all()).containsAll(area.models());
            total += area.models().size();
        }
        assertThat(total).isEqualTo(ValueObjects.all().size());
    }

    @Test
    void enumChoicesArePortedInFull() {
        assertThat(ValueObjects.ADDRESS.field("country").orElseThrow().choices())
                .hasSize(250).startsWith("AFGHANISTAN").contains("PHILIPPINES", "UNKNOWN");
        assertThat(ValueObjects.CURRENCY.field("code").orElseThrow().choices())
                .hasSize(157 + 16).startsWith("UNKNOWN", "AED").contains("PHP", "USD", "BTC").endsWith("USDC");
        assertThat(ValueObjects.TIME.field("meridiem").orElseThrow().choices()).containsExactly("AM", "PM");
        assertThat(ValueObjects.DISTANCE.field("unit").orElseThrow().choices())
                .containsExactly("MILLIMETER", "CENTIMETER", "METER", "KILOMETER", "INCH", "FOOT", "YARD", "MILE",
                                 "NAUTICAL_MILE");
    }

    @Test
    void realisticValuesConvert() {
        assertValid(ValueObjects.MONEY,
                    Map.of("amount", "149.99", "currency", Map.of("type", "FiatCurrency", "code", "PHP")));
        assertValid(ValueObjects.MONEY,
                    Map.of("amount", new BigDecimal("0.00542100"),
                           "currency", Map.of("type", "CryptoCoin", "code", "BTC")));
        assertValid(ValueObjects.EMAIL_ADDRESS, Map.of("value", "first.last+tag@mail.example.com"));
        assertValid(ValueObjects.ADDRESS, Map.of("street", "1 Ayala Ave", "city", "Makati",
                                                 "postalCode", "1226", "country", "PHILIPPINES"));
        assertValid(ValueObjects.ADDRESS, Map.of("country", "UNKNOWN"));
        assertValid(ValueObjects.SEMANTIC_VERSION, Map.of("major", 1, "minor", 2, "patch", 3,
                                                          "preRelease", "rc.1", "buildMetadata", "build.5"));
        assertValid(ValueObjects.SEMANTIC_VERSION, Map.of("major", 0, "minor", 0, "patch", 0));
        assertValid(ValueObjects.COLOR, Map.of("red", 255, "green", 128, "blue", 0));
        assertValid(ValueObjects.COORDINATES, Map.of("latitude", 14.5547, "longitude", 121.0244));
        assertValid(ValueObjects.PHONE_NUMBER, Map.of("countryCode", "63", "nationalNumber", "9171234567"));
        assertValid(ValueObjects.FULL_NAME, Map.of("firstName", "José", "middleName", "de la Cruz",
                                                   "lastName", "O'Brien-Smith"));
        assertValid(ValueObjects.TIME_BLOCK, Map.of("start", Map.of("hour", 10, "minute", 0, "meridiem", "PM"),
                                                    "end", Map.of("hour", 2, "minute", 30, "meridiem", "AM")));
        assertValid(ValueObjects.SSH_CONNECTION, Map.of("host", Map.of("value", "10.0.0.5"),
                                                        "port", Map.of("value", 22), "username", "deploy",
                                                        "identityFile", "~/.ssh/id_ed25519"));
        assertValid(ValueObjects.IP_ADDRESS, Map.of("value", "2001:db8:0:0:0:0:0:1"));
        assertValid(ValueObjects.MAC_ADDRESS, Map.of("value", "00:1A:2B:3C:4D:5E"));
        assertValid(ValueObjects.DATE_TIME_WITH_ZONE, Map.of("value", "2026-10-18T14:00:00+08:00"));
        assertValid(ValueObjects.EFFECTIVE_PERIOD, Map.of("from", "2026-10-18T00:00:00Z"));
        assertValid(ValueObjects.SLUG, Map.of("value", "my-first-post"));
        assertValid(ValueObjects.LINK, Map.of("value", "https://example.com/a?b=c"));
        assertValid(ValueObjects.POSTAL_CODE, Map.of("value", "SW1A 1AA", "country", "UNITED_KINGDOM"));
        assertValid(ValueObjects.DOMAIN_NAME, Map.of("value", "xn--bcher-kva.example"));
    }

    @Test
    void invalidValuesAreRejected() {
        assertInvalid(ValueObjects.MONEY, Map.of("amount", "1.00", "currency",
                                                 Map.of("type", "FiatCurrency", "code", "DOLLARS")));
        assertInvalid(ValueObjects.MONEY, Map.of("amount", "1.00"));
        assertInvalid(ValueObjects.EMAIL_ADDRESS, Map.of("value", "not-an-email"));
        assertInvalid(ValueObjects.EMAIL_ADDRESS, Map.of("value", "a@localhost"));
        assertInvalid(ValueObjects.EMAIL_ADDRESS, Map.of("value", "a@example.123"));
        assertInvalid(ValueObjects.ADDRESS, Map.of("street", "1 Main St"));
        assertInvalid(ValueObjects.ADDRESS, Map.of("country", "ATLANTIS"));
        assertInvalid(ValueObjects.SEMANTIC_VERSION, Map.of("major", 1, "minor", 0, "patch", 0,
                                                            "preRelease", "01"));
        assertInvalid(ValueObjects.COLOR, Map.of("red", "red"));
        assertInvalid(ValueObjects.PHONE_NUMBER, Map.of("countryCode", "+", "nationalNumber", "9171234567"));
        assertInvalid(ValueObjects.FULL_NAME, Map.of("firstName", "R2-D2"));
        assertInvalid(ValueObjects.TIME, Map.of("hour", 10, "minute", 0, "meridiem", "NOON"));
        assertInvalid(ValueObjects.IPV4_ADDRESS, Map.of("value", "256.1.1.1"));
        assertInvalid(ValueObjects.IPV4_ADDRESS, Map.of("value", "01.2.3.4"));
        assertInvalid(ValueObjects.IPV6_ADDRESS, Map.of("value", "2001:db8::zz"));
        assertInvalid(ValueObjects.IP_ADDRESS, Map.of("value", "example.com"));
        assertInvalid(ValueObjects.MAC_ADDRESS, Map.of("value", "00-1A-2B-3C-4D"));
        assertInvalid(ValueObjects.SLUG, Map.of("value", "Not A Slug"));
        assertInvalid(ValueObjects.SLUG, Map.of("value", "a".repeat(101)));
        assertInvalid(ValueObjects.USERNAME, Map.of("value", "ab"));
        assertInvalid(ValueObjects.LINK, Map.of("value", "example.com"));
        assertInvalid(ValueObjects.DATE_TIME_WITH_ZONE, Map.of("value", "2026-10-18T14:00:00"));
        assertInvalid(ValueObjects.SSH_CONNECTION, Map.of("host", Map.of("value", "10.0.0.5"),
                                                          "port", Map.of("value", 22), "username", "bad user"));
    }

    @Test
    void inputIsCleanedUpLikeTheJavaConstructors() {
        assertStored(ValueObjects.EMAIL_ADDRESS, Map.of("value", "  First.Last@Example.COM "),
                     "value", "first.last@example.com");
        assertStored(ValueObjects.USERNAME, Map.of("value", " Joshua.S "), "value", "joshua.s");
        assertStored(ValueObjects.FULL_NAME, Map.of("firstName", "  Juan   Carlos ", "lastName", "   "),
                     "firstName", "Juan Carlos");
        assertThat(convert(ValueObjects.FULL_NAME, Map.of("firstName", "Ana", "lastName", "  ")).values())
                .doesNotContainKey("lastName");
        assertStored(ValueObjects.PHONE_NUMBER, Map.of("countryCode", "+63", "nationalNumber", "(917) 123-4567"),
                     "nationalNumber", "9171234567");
        assertStored(ValueObjects.ADDRESS, Map.of("city", "  ", "country", " philippines"), "country",
                     "PHILIPPINES");
        assertThat(convert(ValueObjects.ADDRESS, Map.of("city", "  ", "country", "PHILIPPINES")).values())
                .doesNotContainKey("city");
        assertStored(ValueObjects.POSTAL_CODE, Map.of("value", " sw1a 1aa ", "country", "UNITED_KINGDOM"),
                     "value", "SW1A 1AA");
        assertStored(ValueObjects.SLUG, Map.of("value", " My-First-Post "), "value", "my-first-post");
        assertStored(ValueObjects.IPV6_ADDRESS, Map.of("value", " 2001:DB8::1 "), "value", "2001:db8:0:0:0:0:0:1");
        assertStored(ValueObjects.IP_ADDRESS, Map.of("value", "::1"), "value", "0:0:0:0:0:0:0:1");
        assertStored(ValueObjects.MAC_ADDRESS, Map.of("value", "00-1a-2b-3c-4d-5e"), "value", "00:1A:2B:3C:4D:5E");
        assertStored(ValueObjects.MAC_ADDRESS, Map.of("value", "001a.2b3c.4d5e"), "value", "00:1A:2B:3C:4D:5E");
        assertStored(ValueObjects.DOMAIN_NAME, Map.of("value", " Bücher.Example "), "value", "xn--bcher-kva.example");
        assertStored(ValueObjects.PERCENTAGE, Map.of("value", "12.345678"), "value", new BigDecimal("12.3457"));
        assertStored(ValueObjects.CURRENCY, Map.of("type", "FiatCurrency", "code", "php"), "code", "PHP");
    }

    @Test
    void limitsAreEnforced() {
        assertInvalid(ValueObjects.PORT, Map.of("value", 70000));
        assertInvalid(ValueObjects.PORT, Map.of("value", -1));
        assertValid(ValueObjects.PORT, Map.of("value", 0));
        assertInvalid(ValueObjects.COORDINATES, Map.of("latitude", 91, "longitude", 0));
        assertInvalid(ValueObjects.COORDINATES, Map.of("latitude", 0, "longitude", -180.5));
        assertInvalid(ValueObjects.TIME, Map.of("hour", 13, "minute", 0, "meridiem", "PM"));
        assertInvalid(ValueObjects.TIME, Map.of("hour", 0, "minute", 0, "meridiem", "AM"));
        assertInvalid(ValueObjects.TIME, Map.of("hour", 12, "minute", 60, "meridiem", "AM"));
        assertInvalid(ValueObjects.COLOR, Map.of("red", 256));
        assertInvalid(ValueObjects.COUNT, Map.of("value", -1));
        assertInvalid(ValueObjects.DURATION_NANO, Map.of("nanos", -5));
        assertInvalid(ValueObjects.DISTANCE, Map.of("value", "-0.01", "unit", "METER"));
        assertInvalid(ValueObjects.RECURRENCE, Map.of("frequency", "DAILY", "interval", 0));
        assertInvalid(ValueObjects.SEMANTIC_VERSION, Map.of("major", -1, "minor", 0, "patch", 0));
        assertInvalid(ValueObjects.SLUG, Map.of("value", "a".repeat(101)));
    }

    @Test
    void rulesAcrossFieldsAreEnforced() {
        assertInvalid(ValueObjects.DATE_RANGE, Map.of("startDate", "2026-10-19", "endDate", "2026-10-18"));
        assertValid(ValueObjects.DATE_RANGE, Map.of("startDate", "2026-10-18", "endDate", "2026-10-18"));
        assertInvalid(ValueObjects.EFFECTIVE_PERIOD, Map.of("from", "2026-10-19T00:00:00.5Z",
                                                            "through", "2026-10-19T00:00:00Z"));
        assertValid(ValueObjects.EFFECTIVE_PERIOD, Map.of("from", "2026-10-19T00:00:00Z",
                                                          "through", "2026-10-19T00:00:00.5Z"));
        assertInvalid(ValueObjects.DATE_OF_BIRTH, Map.of("value", LocalDate.now().plusDays(2).toString()));
        assertValid(ValueObjects.DATE_OF_BIRTH, Map.of("value", "1990-05-01"));
        assertInvalid(ValueObjects.TIME_BLOCK, Map.of("start", Map.of("hour", 9, "minute", 0, "meridiem", "AM"),
                                                      "end", Map.of("hour", 9, "minute", 0, "meridiem", "AM")));
        assertValid(ValueObjects.PHONE_NUMBER, Map.of("countryCode", "63", "nationalNumber", "1234567890123"));
        assertInvalid(ValueObjects.PHONE_NUMBER, Map.of("countryCode", "63", "nationalNumber", "12345678901234"));
        assertInvalid(ValueObjects.CURRENCY, Map.of("type", "CryptoCoin", "code", "USD"));
        assertInvalid(ValueObjects.CURRENCY, Map.of("type", "FiatCurrency", "code", "BTC"));
        assertValid(ValueObjects.CURRENCY, Map.of("type", "CryptoCoin", "code", "BTC"));
        assertInvalid(ValueObjects.POSTAL_CODE, Map.of("value", "1234", "country", "UNITED_STATES"));
        assertValid(ValueObjects.POSTAL_CODE, Map.of("value", "90210-1234", "country", "UNITED_STATES"));
        assertValid(ValueObjects.POSTAL_CODE, Map.of("value", "1226", "country", "PHILIPPINES"));
        Map<String, Object> ssh = Map.of("port", Map.of("value", 22), "username", "deploy");
        assertInvalid(ValueObjects.SSH_CONNECTION, with(ssh, "host", Map.of("value", "0.0.0.0")));
        assertInvalid(ValueObjects.SSH_CONNECTION, with(ssh, "host", Map.of("value", "239.1.2.3")));
        assertInvalid(ValueObjects.SSH_CONNECTION, with(ssh, "host", Map.of("value", "::")));
        assertInvalid(ValueObjects.SSH_CONNECTION, with(ssh, "host", Map.of("value", "FF02::1")));
        assertInvalid(ValueObjects.SSH_CONNECTION, Map.of("host", Map.of("value", "10.0.0.5"),
                                                          "port", Map.of("value", 0), "username", "deploy"));
        assertValid(ValueObjects.SSH_CONNECTION, with(ssh, "host", Map.of("value", "2001:db8::10")));

        // a value object keeps its rules when an entity embeds it
        Model booking = Model.entity("Booking").field(Field.text("id").identity().required())
                .field(Field.model("period", "date-range").required()).build();
        RecordValues.Result result = RecordValues.convert(booking, SCHEMA.with(booking), Map.of(
                "id", "b-1", "period", Map.of("startDate", "2026-12-24", "endDate", "2026-12-20")), "record");
        assertThat(result.problems()).extracting(p -> p.message())
                .containsExactly("record.period: the end date cannot be before the start date");
    }

    @Test
    void installsIntoAModelServiceOnce() {
        ModelService service = ModelService.inMemory();
        List<Model> created = ValueObjects.install(service);
        assertThat(created).containsExactlyElementsOf(ValueObjects.all());
        assertThat(service.listModels()).hasSize(ValueObjects.all().size());

        assertThat(ValueObjects.install(service)).isEmpty();
        assertThat(service.listModels()).hasSize(ValueObjects.all().size());
    }

    @Test
    void installSkipsModelsThatAlreadyExist() {
        ModelService service = ModelService.inMemory();
        Model ownAddress = Model.valueObject("Address").field(Field.text("line").required()).build();
        service.createModel(ownAddress);

        List<Model> created = ValueObjects.install(service);

        assertThat(created).hasSize(ValueObjects.all().size() - 1).doesNotContain(ValueObjects.ADDRESS);
        assertThat(service.getModel("address")).contains(ownAddress);
    }

    @Test
    void installsIntoASchemaBuilder() {
        Model ownMoney = Model.valueObject("Money").field(Field.decimal("cents")).build();
        Schema schema = ValueObjects.install(Schema.builder().model(ownMoney)).build();

        assertThat(schema.models()).hasSize(ValueObjects.all().size());
        assertThat(schema.model("money")).contains(ownMoney);
        assertThat(ValueObjects.install(ValueObjects.install(Schema.builder())).build().models())
                .hasSize(ValueObjects.all().size());
    }

    @Test
    void entitiesCanHoldThePredefinedValueObjects() {
        Model product = Model.entity("Product")
                .field(Field.text("sku").identity().required())
                .field(Field.model("price", "money").required())
                .field(Field.model("weight", "weight"))
                .build();
        Schema schema = SCHEMA.with(product);

        RecordValues.Result result = RecordValues.convert(product, schema, Map.of(
                "sku", "p-1",
                "price", Map.of("amount", 99.5, "currency", Map.of("type", "FiatCurrency", "code", "USD")),
                "weight", Map.of("value", "1.25", "unit", "KILOGRAM")), "record");

        assertThat(result.problems()).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> price = (Map<String, Object>) result.values().get("price");
        assertThat(price.get("amount")).isEqualTo(new BigDecimal("99.5"));
    }

    private static RecordValues.Result convert(Model model, Map<String, ?> values) {
        return RecordValues.convert(model, SCHEMA, values, "x");
    }

    private static void assertStored(Model model, Map<String, ?> values, String field, Object expected) {
        RecordValues.Result result = convert(model, values);
        assertThat(result.problems()).as(model.id() + " " + values).isEmpty();
        assertThat(result.values()).as(model.id() + " " + values).containsEntry(field, expected);
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> out = new HashMap<>(base);
        out.put(key, value);
        return out;
    }

    private static void assertValid(Model model, Map<String, ?> values) {
        RecordValues.Result result = RecordValues.convert(model, SCHEMA, values, "x");
        assertThat(result.problems()).as(model.id() + " " + values).isEmpty();
    }

    private static void assertInvalid(Model model, Map<String, ?> values) {
        RecordValues.Result result = RecordValues.convert(model, SCHEMA, values, "x");
        assertThat(result.valid()).as(model.id() + " " + values + " should be rejected").isFalse();
    }
}
