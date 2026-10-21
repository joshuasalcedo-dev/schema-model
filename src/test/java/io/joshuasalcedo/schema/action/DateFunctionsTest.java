package io.joshuasalcedo.schema.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DateFunctionsTest {

    private final Expressions expressions = new Expressions();

    private Object eval(String expression, Map<String, ?> variables) {
        return expressions.eval(expression, variables);
    }

    @Test
    void addsDaysAndMonths() {
        assertThat(eval("addDays('2026-10-19', 10)", Map.of())).isEqualTo("2026-10-29");
        assertThat(eval("addDays('2026-10-19', -19)", Map.of())).isEqualTo("2026-09-30");
        assertThat(eval("addMonths('2026-10-19', 1)", Map.of())).isEqualTo("2026-11-19");
        assertThat(eval("addMonths('2026-01-31', 1)", Map.of())).isEqualTo("2026-02-28");
        assertThat(eval("addMonths('2026-10-19', 3)", Map.of())).isEqualTo("2027-01-19");
    }

    @Test
    void countsDaysBetween() {
        assertThat(eval("daysBetween('2026-10-19', '2026-10-22')", Map.of())).isEqualTo(3L);
        assertThat(eval("daysBetween('2026-10-22', '2026-10-19')", Map.of())).isEqualTo(-3L);
    }

    @Test
    void addsMinutesAndHours() {
        assertThat(eval("addMinutes('2026-10-19T06:00:00.000000000Z', 90)", Map.of()))
                .isEqualTo("2026-10-19T07:30:00.000000000Z");
        assertThat(eval("addHours('2026-10-19T06:00:00Z', -24)", Map.of())).isEqualTo("2026-10-18T06:00:00.000000000Z");
        assertThat(eval("addHours('2026-10-19', 6)", Map.of())).isEqualTo("2026-10-19T06:00:00.000000000Z");
        // a conditional step: free to cancel up to 24 hours before, late after that
        assertThat(eval("now <= addHours(record.startsAt, -24) ? 'cancelled' : 'late-cancelled'",
                Map.of("now", "2026-10-19T10:00:00.000000000Z", "record", Map.of("startsAt", "2026-10-20T08:00:00.000000000Z"))))
                .isEqualTo("late-cancelled");
    }

    @Test
    void takesAnInstantByItsDate() {
        assertThat(eval("addDays(now, 1)", Map.of("now", "2026-10-19T23:59:00.000000000Z"))).isEqualTo("2026-10-20");
    }

    @Test
    void worksOnRecordsAndInputs() {
        // a gym renewal: the new end date from the plan's length, as an action's set step would write it
        Object end = eval("input.unit == 'months' ? addMonths(record.endDate, input.length) : addDays(record.endDate, input.length)",
                Map.of("record", Map.of("endDate", "2026-10-31"), "input", Map.of("unit", "months", "length", 1L)));
        assertThat(end).isEqualTo("2026-11-30");
        // and a rule: still valid today
        assertThat(eval("daysBetween(today, record.endDate) >= 0", Map.of("today", "2026-10-19",
                "record", Map.of("endDate", "2026-10-31")))).isEqualTo(true);
    }

    @Test
    void checksWhenSaved() {
        assertThat(expressions.check("addDays(today, 7) > today", true)).isEmpty();
        assertThat(expressions.check("addDays(today)", false)).isPresent();
    }

    @Test
    void refusesWhatIsNotADate() {
        assertThatThrownBy(() -> eval("addDays('next week', 1)", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a date");
    }
}
