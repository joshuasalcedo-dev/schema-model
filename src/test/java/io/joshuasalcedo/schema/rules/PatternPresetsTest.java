package io.joshuasalcedo.schema.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Normalize;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.record.RecordValues;

class PatternPresetsTest {

    private static final Map<String, List<String>> GOOD = Map.ofEntries(
            Map.entry("email", List.of("ana@example.com", " Ana@Example.COM ")),
            Map.entry("url", List.of("https://example.com", "http://example.com/a?b=c#d")),
            Map.entry("digits", List.of("0", "0123456789")),
            Map.entry("letters", List.of("abc", "ABCdef")),
            Map.entry("alphanumeric", List.of("abc123", "A1")),
            Map.entry("slug", List.of("summer-sale-2026", "Abc")),
            Map.entry("code", List.of("ORDER_PLACED", "order_placed", "A1")),
            Map.entry("hex-color", List.of("#1A2B3C", "#abcdef")),
            Map.entry("phone-e164", List.of("+639171234567", "1234567")),
            Map.entry("uuid", List.of("123e4567-e89b-12d3-a456-426614174000", "123E4567-E89B-12D3-A456-426614174000")),
            Map.entry("ipv4", List.of("192.168.1.10", "0.0.0.0", "255.255.255.255")),
            Map.entry("semver", List.of("1.4.2", "2.0.0-rc.1+build.5", "0.0.1")),
            Map.entry("country-code", List.of("PH", "de")),
            Map.entry("currency-code", List.of("USD", "eur")));

    private static final Map<String, List<String>> BAD = Map.ofEntries(
            Map.entry("email", List.of("ana", "ana@example", "a b@example.com")),
            Map.entry("url", List.of("example.com", "ftp://example.com", "https://")),
            Map.entry("digits", List.of("", "12a", "-1")),
            Map.entry("letters", List.of("abc1", "a b")),
            Map.entry("alphanumeric", List.of("a-b", "a b")),
            Map.entry("slug", List.of("-a", "a--b", "a_b")),
            Map.entry("code", List.of("A-B", "A B")),
            Map.entry("hex-color", List.of("1A2B3C", "#12345", "#GGGGGG")),
            Map.entry("phone-e164", List.of("123456", "+1234567890123456", "+63 917")),
            Map.entry("uuid", List.of("123e4567e89b12d3a456426614174000", "123e4567-e89b-12d3-a456-42661417400")),
            Map.entry("ipv4", List.of("256.1.1.1", "1.2.3", "01.2.3.4")),
            Map.entry("semver", List.of("1.2", "01.2.3", "1.2.3-")),
            Map.entry("country-code", List.of("PHL", "P1")),
            Map.entry("currency-code", List.of("US", "US1")));

    @Test
    void everyPresetAcceptsAndRefusesItsExamples() {
        assertThat(PatternPresets.all()).extracting(PatternPreset::id).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(GOOD.keySet());
        assertThat(BAD.keySet()).isEqualTo(GOOD.keySet());
        for (PatternPreset preset : PatternPresets.all()) {
            assertThat(Pattern.compile(preset.pattern())).isNotNull();
            assertThat(preset.name()).isNotBlank();
            assertThat(preset.description()).isNotBlank();
            for (String good : GOOD.get(preset.id())) {
                assertThat(preset.matches(good)).as(preset.id() + " accepts '" + good + "'").isTrue();
            }
            for (String bad : BAD.get(preset.id())) {
                assertThat(preset.matches(bad)).as(preset.id() + " refuses '" + bad + "'").isFalse();
            }
        }
    }

    @Test
    void aFieldWithAPresetBehavesTheSame() {
        PatternPreset email = PatternPresets.byId("email").orElseThrow();
        Model model = Model.valueObject("Contact")
                .field(Field.text("email").pattern(email.pattern())
                               .normalize(email.normalize().toArray(Normalize[]::new)))
                .build();
        Schema schema = Schema.builder().model(model).build();
        RecordValues.Result ok = RecordValues.convert(model, schema, Map.of("email", " Ana@Example.com"), "x");
        assertThat(ok.valid()).isTrue();
        assertThat(ok.values()).containsEntry("email", "ana@example.com");
        assertThat(RecordValues.convert(model, schema, Map.of("email", "nope"), "x").valid()).isFalse();
    }

    @Test
    void lookupAndRecognize() {
        assertThat(PatternPresets.byId("uuid")).isPresent();
        assertThat(PatternPresets.byId("nope")).isEmpty();
        assertThat(PatternPresets.byId(null)).isEmpty();
        for (PatternPreset preset : PatternPresets.all()) {
            assertThat(PatternPresets.recognize(preset.pattern())).contains(preset);
            assertThat(PatternPresets.recognize(" " + preset.pattern() + " ")).contains(preset);
        }
        assertThat(PatternPresets.recognize("[A-Z]{4}")).isEmpty();
        assertThat(PatternPresets.recognize("")).isEmpty();
        assertThat(PatternPresets.recognize(null)).isEmpty();
    }
}
