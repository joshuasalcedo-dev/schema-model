package io.joshuasalcedo.schema.action;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelFunctionsTest {

    private final Expressions expressions = new Expressions();

    private Object eval(String expression) {
        return expressions.eval(expression, Map.of());
    }

    private Object eval(String expression, Map<String, ?> record) {
        return expressions.eval(expression, Map.of("record", record));
    }

    private static final Map<String, Object> BILL = Map.of(
            "lines", List.of(
                    Map.of("net", 100.0, "tax", 5.0, "paid", true),
                    Map.of("net", 250.5, "tax", 12.53, "paid", false)),
            "bill_date", "2026-11-21",
            "payment_terms_days", 30L,
            "unit_price", Map.of("amount", 12.5, "currency", "QAR"),
            "quantity", 3L);

    @Test
    void sumsAndCountsComponentLists() {
        assertThat(eval("SUM(record.lines.map(l, l.net))", BILL)).isEqualTo(350.5);
        assertThat(eval("SUM(record.lines.filter(l, l.paid).map(l, l.net))", BILL)).isEqualTo(100.0);
        assertThat(eval("COUNT(record.lines)", BILL)).isEqualTo(2L);
        assertThat(eval("SUM([1, 2, 3])")).isEqualTo(6L);
        assertThat(eval("AVG([1, 2, 3, 4])")).isEqualTo(2.5);
        assertThat(eval("MAX([3, 9.5, 1])")).isEqualTo(9.5);
        assertThat(eval("MIN([3, 9.5, 1])")).isEqualTo(1.0);
        assertThat(eval("SUM([])")).isEqualTo(0L);
    }

    @Test
    void mixesWholeAndDecimalNumbersLikeASpreadsheet() {
        assertThat(eval("ROUND(PERCENT(AMOUNT(record.unit_price), 5), 2)", BILL)).isEqualTo(0.63);
        assertThat(eval("ROUND(2.5)")).isEqualTo(3.0);
        assertThat(eval("ROUND(1.005, 2)")).isEqualTo(1.01);
        assertThat(eval("FLOOR(2.7)")).isEqualTo(2.0);
        assertThat(eval("CEIL(2.1)")).isEqualTo(3.0);
        assertThat(eval("ABS(-4)")).isEqualTo(4.0);
        assertThat(eval("PERCENT(record.quantity, 50)", BILL)).isEqualTo(1.5);
    }

    @Test
    void handlesEmptyValues() {
        assertThat(eval("IS_EMPTY(null)")).isEqualTo(true);
        assertThat(eval("IS_EMPTY('')")).isEqualTo(true);
        assertThat(eval("IS_EMPTY([])")).isEqualTo(true);
        assertThat(eval("IS_EMPTY('x')")).isEqualTo(false);
        assertThat(eval("COALESCE(null, 0)")).isEqualTo(0L);
        assertThat(eval("COALESCE('', null, 'third')")).isEqualTo("third");
        assertThat(eval("IS_EMPTY(ROUND(null))")).isEqualTo(true);
        assertThat(eval("EXISTS([null, 1])")).isEqualTo(true);
        assertThat(eval("IF(1 > 2, 'yes', 'no')")).isEqualTo("no");
    }

    @Test
    void doesDateArithmetic() {
        assertThat(eval("DATE_ADD(record.bill_date, COALESCE(record.payment_terms_days, 0), 'D')", BILL)).isEqualTo("2026-12-21");
        assertThat(eval("DATE_ADD('2026-01-31', 1, 'M')")).isEqualTo("2026-02-28");
        assertThat(eval("DATE_SUB('2026-11-21', 1, 'Y')")).isEqualTo("2025-11-21");
        assertThat(eval("DATE_ADD('2026-11-21T06:00:00Z', 4, 'H')")).isEqualTo("2026-11-21T10:00:00.000000000Z");
        assertThat(eval("DAYS_BETWEEN('2026-11-01', '2026-11-21')")).isEqualTo(20L);
        assertThat(eval("MONTHS_BETWEEN('2026-01-15', '2026-11-21')")).isEqualTo(10L);
        assertThat(eval("HOURS('2026-11-21T06:00:00Z', '2026-11-21T10:30:00Z')")).isEqualTo(4.5);
        assertThat(eval("HOURS('PT4H30M')")).isEqualTo(4.5);
        assertThat(eval("MINUTES('P1D')")).isEqualTo(1440.0);
        assertThat(eval("YEAR('2026-11-21') * 100 + MONTH('2026-11-21')")).isEqualTo(202611L);
        assertThat(eval("START_OF('2026-11-21', 'M')")).isEqualTo("2026-11-01");
        assertThat(eval("END_OF('2026-02-10', 'M')")).isEqualTo("2026-02-28");
        assertThat(eval("END_OF('2026-02-10', 'Y')")).isEqualTo("2026-12-31");
    }

    @Test
    void worksOnTextAndMoney() {
        assertThat(eval("CONCAT('WO-', 123)")).isEqualTo("WO-123");
        assertThat(eval("CONCAT(['a', null, 'b'])")).isEqualTo("ab");
        assertThat(eval("UPPER(TRIM('  qar '))")).isEqualTo("QAR");
        assertThat(eval("LENGTH('abc') + LENGTH([1, 2])")).isEqualTo(5L);
        assertThat(eval("MONEY(record.quantity * AMOUNT(record.unit_price), CURRENCY_OF(record.unit_price))",
                Map.of("quantity", 3.0, "unit_price", Map.of("amount", 12.5, "currency", "QAR"))))
                .isEqualTo(Map.of("amount", 37.5, "currency", "QAR"));
        assertThat(eval("CURRENCY_OF(record.unit_price)", BILL)).isEqualTo("QAR");
    }

    @Test
    void isARuleToo() {
        assertThat(expressions.check("SUM(record.lines.map(l, l.net)) > 0", true)).isEmpty();
        assertThat(expressions.check("NOPE(1)", false)).isPresent();
    }

    @Test
    void saysWhatIsWrong() {
        assertThatThrownBy(() -> eval("DATE_ADD('2026-11-21', 1, 'Q')")).hasMessageContaining("not a unit");
        assertThatThrownBy(() -> eval("ROUND('abc')")).hasMessageContaining("not a number");
        assertThatThrownBy(() -> eval("HOURS('P1M')")).hasMessageContaining("months or years");
    }
}
