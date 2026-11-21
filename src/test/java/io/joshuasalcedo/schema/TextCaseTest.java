package io.joshuasalcedo.schema;

import io.joshuasalcedo.schema.action.Expressions;
import io.joshuasalcedo.schema.record.RecordValues;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextCaseTest {

    @Test
    void readsAnyCaseAndWritesEach() {
        for (String input : new String[]{"Metro Station", "metro_station", "metro-station", "metroStation", "MetroStation",
                "METRO_STATION", "  metro   station ", "metro.station"}) {
            assertThat(TextCase.SNAKE.apply(input)).as(input).isEqualTo("metro_station");
            assertThat(TextCase.KEBAB.apply(input)).as(input).isEqualTo("metro-station");
            assertThat(TextCase.CAMEL.apply(input)).as(input).isEqualTo("metroStation");
            assertThat(TextCase.PASCAL.apply(input)).as(input).isEqualTo("MetroStation");
            assertThat(TextCase.CONSTANT.apply(input)).as(input).isEqualTo("METRO_STATION");
            assertThat(TextCase.TITLE.apply(input)).as(input).isEqualTo("Metro Station");
            assertThat(TextCase.SENTENCE.apply(input)).as(input).isEqualTo("Metro station");
        }
        assertThat(TextCase.SNAKE.apply("HVACUnit2")).isEqualTo("hvac_unit2");
        assertThat(TextCase.SNAKE.apply("zone2Platform")).isEqualTo("zone2_platform");
        assertThat(TextCase.of("Title")).isEqualTo(TextCase.TITLE);
        assertThatThrownBy(() -> TextCase.of("wavy")).hasMessageContaining("not a text case");
    }

    @Test
    void aFieldConvertsWhatIsTyped() {
        Model model = Model.valueObject("FacilityTypeInput")
                .field(Field.text("facilityType").required().normalize(Normalize.TRIM, Normalize.SNAKE_CASE)
                               .pattern(TextCase.SNAKE.pattern()))
                .build();
        Schema schema = Schema.builder().model(model).build();
        RecordValues.Result typed = RecordValues.convert(model, schema, Map.of("facilityType", " Metro Station "), "x");
        assertThat(typed.valid()).isTrue();
        assertThat(typed.values()).containsEntry("facilityType", "metro_station");
    }

    @Test
    void aFieldCanRequireTheCaseWithoutConverting() {
        Model model = Model.valueObject("StrictCode")
                .field(Field.text("code").required().pattern(TextCase.SNAKE.pattern()))
                .build();
        Schema schema = Schema.builder().model(model).build();
        assertThat(RecordValues.convert(model, schema, Map.of("code", "metro_station"), "x").valid()).isTrue();
        assertThat(RecordValues.convert(model, schema, Map.of("code", "Metro Station"), "x").valid()).isFalse();
    }

    @Test
    void expressionsFormatForDisplay() {
        Expressions expressions = new Expressions();
        assertThat(expressions.eval("TO_CASE(record.type, 'title')", Map.of("record", Map.of("type", "metro_station"))))
                .isEqualTo("Metro Station");
        assertThat(expressions.eval("FORMAT(1234567.891, '#,##0.00')", Map.of())).isEqualTo("1,234,567.89");
        assertThat(expressions.eval("FORMAT('2026-11-21', 'dd MMM yyyy')", Map.of())).isEqualTo("21 Nov 2026");
        assertThat(expressions.eval("FORMAT('2026-11-21T06:05:00Z', 'yyyy-MM-dd HH:mm')", Map.of())).isEqualTo("2026-11-21 06:05");
        assertThat(expressions.eval("IN_WORDS(1250.5)", Map.of())).isEqualTo("one thousand two hundred fifty and 50/100");
        assertThat(expressions.eval("IN_WORDS(2000015)", Map.of())).isEqualTo("two million fifteen");
        assertThat(expressions.eval("IN_WORDS(0)", Map.of())).isEqualTo("zero");
        assertThat(expressions.eval("IN_WORDS(-21.05)", Map.of())).isEqualTo("minus twenty-one and 05/100");
    }
}
