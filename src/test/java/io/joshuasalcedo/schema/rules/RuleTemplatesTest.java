package io.joshuasalcedo.schema.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Invariant;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.action.Expressions;
import io.joshuasalcedo.schema.record.RecordValues;

class RuleTemplatesTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    /** A model with a field of every kind the templates use; nothing required except {@code start}. */
    private static final Model SAMPLE = Model.valueObject("Sample")
            .field(Field.date("start").required())
            .field(Field.date("end"))
            .field(Field.date("due"))
            .field(Field.timestamp("createdAt"))
            .field(Field.timestamp("updatedAt"))
            .field(Field.decimal("price"))
            .field(Field.decimal("discount"))
            .field(Field.integer("quantity"))
            .field(Field.integer("stock"))
            .field(Field.choice("status", "open", "closed", "it's done"))
            .field(Field.text("reason"))
            .field(Field.text("email"))
            .field(Field.text("phone"))
            .field(Field.bool("urgent"))
            .field(Field.text("tags").repeated())
            .build();

    private static RuleTemplate template(String id) {
        return RuleTemplates.byId(id).orElseThrow();
    }

    /** Creates the rule, checks it compiles and recognises, and checks which values pass. */
    private static Invariant check(String id, Map<String, String> arguments, List<Map<String, ?>> pass,
                                   List<Map<String, ?>> fail) {
        Invariant rule = template(id).create(SAMPLE, arguments);
        assertThat(Expressions.shared().check(rule.expression(), true)).as(rule.expression()).isEmpty();
        Model model = SAMPLE.toBuilder().invariant(rule.expression(), rule.message()).build();
        Schema schema = Schema.builder().model(model).build();
        for (Map<String, ?> values : pass) {
            assertThat(RecordValues.convert(model, schema, values, "x", NOW).problems())
                    .as(rule.expression() + " should pass " + values).isEmpty();
        }
        for (Map<String, ?> values : fail) {
            assertThat(RecordValues.convert(model, schema, values, "x", NOW).problems())
                    .as(rule.expression() + " should fail " + values)
                    .singleElement().satisfies(p -> assertThat(p.message()).endsWith(rule.message()));
        }
        assertThat(RuleTemplates.recognize(rule, SAMPLE)).as("recognize " + rule.expression())
                .hasValueSatisfying(m -> {
                    assertThat(m.template().id()).isEqualTo(id);
                    assertThat(m.template().create(SAMPLE, m.arguments())).isEqualTo(rule);
                });
        return rule;
    }

    @Test
    void notBefore() {
        Invariant rule = check("not-before", Map.of("field", "end", "other", "start"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "end", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "end", "2026-02-01")),
                               List.of(Map.of("start", "2026-01-02", "end", "2026-01-01")));
        assertThat(rule.expression()).isEqualTo("!has(value.end) || value.end >= value.start");
        assertThat(rule.message()).isEqualTo("End cannot be before start");
    }

    @Test
    void notBeforeWithTwoOptionalDates() {
        Invariant rule = check("not-before", Map.of("field", "due", "other", "end"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "due", "2020-01-01"),
                                       Map.of("start", "2026-01-01", "end", "2020-01-01")),
                               List.of(Map.of("start", "2026-01-01", "end", "2026-03-01", "due", "2026-02-01")));
        assertThat(rule.expression()).isEqualTo("!has(value.due) || !has(value.end) || value.due >= value.end");
    }

    @Test
    void after() {
        Invariant rule = check("after", Map.of("field", "updatedAt", "other", "createdAt"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "createdAt", "2026-01-01T10:00:00Z",
                                              "updatedAt", "2026-01-01T10:00:00.5Z")),
                               List.of(Map.of("start", "2026-01-01", "createdAt", "2026-01-01T10:00:00Z",
                                              "updatedAt", "2026-01-01T10:00:00Z")));
        assertThat(rule.expression())
                .isEqualTo("!has(value.updatedAt) || !has(value.createdAt) || value.updatedAt > value.createdAt");
    }

    @Test
    void notInFutureUsesTodayForDatesAndNowForTimestamps() {
        Invariant date = check("not-in-future", Map.of("field", "start"),
                               List.of(Map.of("start", "2026-10-19"), Map.of("start", "2000-01-01")),
                               List.of(Map.of("start", "2026-10-20")));
        assertThat(date.expression()).isEqualTo("value.start <= today");
        Invariant time = check("not-in-future", Map.of("field", "createdAt"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "createdAt", "2026-10-19T11:59:59Z")),
                               List.of(Map.of("start", "2026-01-01", "createdAt", "2026-10-19T12:00:01Z")));
        assertThat(time.expression()).isEqualTo("!has(value.createdAt) || value.createdAt <= now");
        assertThat(time.message()).isEqualTo("Created at cannot be in the future");
    }

    @Test
    void notInPast() {
        Invariant rule = check("not-in-past", Map.of("field", "due"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "due", "2026-10-19")),
                               List.of(Map.of("start", "2026-01-01", "due", "2026-10-18")));
        assertThat(rule.expression()).isEqualTo("!has(value.due) || value.due >= today");
    }

    @Test
    void different() {
        Invariant rule = check("different", Map.of("field", "email", "other", "phone"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "email", "a"),
                                       Map.of("start", "2026-01-01", "email", "a", "phone", "b")),
                               List.of(Map.of("start", "2026-01-01", "email", "a", "phone", "a")));
        assertThat(rule.message()).isEqualTo("Email and phone must differ");
    }

    @Test
    void atMostAndLessThan() {
        check("at-most", Map.of("field", "discount", "other", "price"),
              List.of(Map.of("start", "2026-01-01", "discount", "5"),
                      Map.of("start", "2026-01-01", "discount", "5", "price", "5")),
              List.of(Map.of("start", "2026-01-01", "discount", "5.01", "price", "5")));
        Invariant lessThan = check("less-than", Map.of("field", "quantity", "other", "stock"),
                                   List.of(Map.of("start", "2026-01-01", "quantity", 3, "stock", 4)),
                                   List.of(Map.of("start", "2026-01-01", "quantity", 4, "stock", 4)));
        assertThat(lessThan.expression())
                .isEqualTo("!has(value.quantity) || !has(value.stock) || value.quantity < value.stock");
    }

    @Test
    void numbersOfDifferentTypesAreComparedAsDoubles() {
        Invariant rule = check("at-most", Map.of("field", "quantity", "other", "price"),
                               List.of(Map.of("start", "2026-01-01", "quantity", 5, "price", "5.0"),
                                       Map.of("start", "2026-01-01", "quantity", 5, "price", "5.5")),
                               List.of(Map.of("start", "2026-01-01", "quantity", 6, "price", "5.5")));
        assertThat(rule.expression()).contains("double(value.quantity) <= value.price");
    }

    @Test
    void totalAtMost() {
        Invariant integers = check("total-at-most", Map.of("fields", "quantity, stock", "limit", "10"),
                                   List.of(Map.of("start", "2026-01-01"),
                                           Map.of("start", "2026-01-01", "quantity", 10),
                                           Map.of("start", "2026-01-01", "quantity", 4, "stock", 6)),
                                   List.of(Map.of("start", "2026-01-01", "stock", 11),
                                           Map.of("start", "2026-01-01", "quantity", 5, "stock", 6)));
        assertThat(integers.expression())
                .isEqualTo("(has(value.quantity) ? value.quantity : 0) + (has(value.stock) ? value.stock : 0) <= 10");
        assertThat(integers.message()).isEqualTo("The total of quantity and stock cannot be more than 10");

        Invariant mixed = check("total-at-most", Map.of("fields", "price,discount,quantity", "limit", "100"),
                                List.of(Map.of("start", "2026-01-01", "price", "50.5", "quantity", 49)),
                                List.of(Map.of("start", "2026-01-01", "price", "50.5", "discount", "0.5",
                                               "quantity", 50)));
        assertThat(mixed.expression()).endsWith("(has(value.quantity) ? double(value.quantity) : 0.0) <= 100.0");
    }

    @Test
    void atLeastOne() {
        Invariant rule = check("at-least-one", Map.of("fields", "email, phone, tags"),
                               List.of(Map.of("start", "2026-01-01", "phone", "1"),
                                       Map.of("start", "2026-01-01", "tags", List.of("a"))),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "tags", List.of())));
        assertThat(rule.expression()).isEqualTo(
                "has(value.email) || has(value.phone) || (has(value.tags) && size(value.tags) > 0)");
        assertThat(rule.message()).isEqualTo("At least one of email, phone or tags is required");
    }

    @Test
    void onlyOneAndExactlyOne() {
        check("only-one", Map.of("fields", "email, phone"),
              List.of(Map.of("start", "2026-01-01"), Map.of("start", "2026-01-01", "email", "a")),
              List.of(Map.of("start", "2026-01-01", "email", "a", "phone", "b")));
        check("exactly-one", Map.of("fields", "email, phone"),
              List.of(Map.of("start", "2026-01-01", "phone", "b")),
              List.of(Map.of("start", "2026-01-01"), Map.of("start", "2026-01-01", "email", "a", "phone", "b")));
    }

    @Test
    void requiredTogether() {
        Invariant rule = check("required-together", Map.of("fields", "price, discount"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "price", 1, "discount", 0)),
                               List.of(Map.of("start", "2026-01-01", "price", 1),
                                       Map.of("start", "2026-01-01", "discount", 1)));
        assertThat(rule.expression()).isEqualTo(
                "(has(value.price) && has(value.discount)) || (!has(value.price) && !has(value.discount))");
    }

    @Test
    void requiredWhen() {
        Invariant rule = check("required-when", Map.of("target", "reason", "condition", "status", "value", "closed"),
                               List.of(Map.of("start", "2026-01-01"),
                                       Map.of("start", "2026-01-01", "status", "open"),
                                       Map.of("start", "2026-01-01", "status", "closed", "reason", "done")),
                               List.of(Map.of("start", "2026-01-01", "status", "closed")));
        assertThat(rule.expression())
                .isEqualTo("!(has(value.status) && value.status == 'closed') || has(value.reason)");
        assertThat(rule.message()).isEqualTo("Reason is required when status is closed");

        Invariant quoted = check("required-when",
                                 Map.of("target", "reason", "condition", "status", "value", "it's done"),
                                 List.of(Map.of("start", "2026-01-01", "status", "closed")),
                                 List.of(Map.of("start", "2026-01-01", "status", "it's done")));
        assertThat(quoted.expression()).contains("value.status == 'it\\'s done'");

        check("required-when", Map.of("target", "tags", "condition", "urgent", "value", "TRUE"),
              List.of(Map.of("start", "2026-01-01", "urgent", false),
                      Map.of("start", "2026-01-01", "urgent", true, "tags", List.of("x"))),
              List.of(Map.of("start", "2026-01-01", "urgent", true, "tags", List.of())));
        check("required-when", Map.of("target", "email", "condition", "quantity", "value", "-3"),
              List.of(Map.of("start", "2026-01-01", "quantity", 3)),
              List.of(Map.of("start", "2026-01-01", "quantity", -3)));
        check("required-when", Map.of("target", "email", "condition", "reason", "value", "a\\b"),
              List.of(Map.of("start", "2026-01-01", "reason", "ab")),
              List.of(Map.of("start", "2026-01-01", "reason", "a\\b")));
    }

    @Test
    void requiredWhenARequiredConditionNeedsNoGuard() {
        Model model = Model.valueObject("Ticket")
                .field(Field.choice("status", "open", "closed").required())
                .field(Field.text("reason"))
                .build();
        Invariant rule = template("required-when")
                .create(model, Map.of("target", "reason", "condition", "status", "value", "closed"));
        assertThat(rule.expression()).isEqualTo("!(value.status == 'closed') || has(value.reason)");
        assertThat(RuleTemplates.recognize(rule, model)).isPresent();
    }

    @Test
    void forbiddenWhen() {
        Invariant rule = check("forbidden-when", Map.of("target", "reason", "condition", "status", "value", "open"),
                               List.of(Map.of("start", "2026-01-01", "reason", "x"),
                                       Map.of("start", "2026-01-01", "status", "open"),
                                       Map.of("start", "2026-01-01", "status", "closed", "reason", "x")),
                               List.of(Map.of("start", "2026-01-01", "status", "open", "reason", "x")));
        assertThat(rule.message()).isEqualTo("Reason must be empty when status is open");
    }

    @Test
    void everyTemplateIsCoveredAndHasUniqueIds() {
        assertThat(RuleTemplates.all()).extracting(RuleTemplate::id).doesNotHaveDuplicates().containsExactly(
                "not-before", "after", "not-in-future", "not-in-past", "different", "at-most", "less-than",
                "total-at-most", "at-least-one", "only-one", "exactly-one", "required-together", "required-when",
                "forbidden-when");
        assertThat(RuleTemplates.byId("nope")).isEmpty();
        assertThat(RuleTemplates.byId(null)).isEmpty();
        assertThat(RuleTemplates.applicable(SAMPLE)).isEqualTo(RuleTemplates.all());
    }

    @Test
    void badArgumentsAreAllReported() {
        assertThatThrownBy(() -> template("not-before")
                .create(SAMPLE, Map.of("field", "nope", "other", "price", "extra", "x")))
                .isInstanceOfSatisfying(SchemaException.class, e -> {
                    assertThat(e.reason()).isEqualTo(SchemaException.Reason.INVALID);
                    assertThat(e.problems()).extracting(p -> p.path()).containsExactlyInAnyOrder(
                            "arguments.extra", "arguments.field", "arguments.other");
                });
        assertThatThrownBy(() -> template("not-before").create(SAMPLE, Map.of("field", "end")))
                .hasMessageContaining("arguments.other is required");
        // a date and a timestamp are not compared
        assertThatThrownBy(() -> template("not-before").create(SAMPLE, Map.of("field", "end", "other", "createdAt")))
                .hasMessageContaining("must have the same type as 'end'");
        assertThatThrownBy(() -> template("different").create(SAMPLE, Map.of("field", "email", "other", "email")))
                .hasMessageContaining("already used");
        assertThatThrownBy(() -> template("different").create(SAMPLE, Map.of("field", "tags", "other", "email")))
                .hasMessageContaining("single (not repeated)");
        assertThatThrownBy(() -> template("at-least-one").create(SAMPLE, Map.of("fields", "email")))
                .hasMessageContaining("at least two fields");
        assertThatThrownBy(() -> template("at-least-one").create(SAMPLE, Map.of("fields", "email, email")))
                .hasMessageContaining("more than once");
        assertThatThrownBy(() -> template("required-when")
                .create(SAMPLE, Map.of("target", "reason", "condition", "status", "value", "pending")))
                .hasMessageContaining("arguments.value 'pending' is not one of");
        assertThatThrownBy(() -> template("required-when")
                .create(SAMPLE, Map.of("target", "reason", "condition", "urgent", "value", "yes")))
                .hasMessageContaining("must be true or false");
        assertThatThrownBy(() -> template("required-when")
                .create(SAMPLE, Map.of("target", "reason", "condition", "quantity", "value", "1.5")))
                .hasMessageContaining("not a whole number");
        assertThatThrownBy(() -> template("required-when")
                .create(SAMPLE, Map.of("target", "reason", "condition", "price", "value", "1")))
                .hasMessageContaining("arguments.condition");
        assertThatThrownBy(() -> template("total-at-most")
                .create(SAMPLE, Map.of("fields", "quantity, stock", "limit", "2.5")))
                .hasMessageContaining("arguments.limit '2.5' is not a whole number");
        assertThatThrownBy(() -> template("total-at-most")
                .create(SAMPLE, Map.of("fields", "price, stock", "limit", "lots")))
                .hasMessageContaining("arguments.limit 'lots' is not a number");
    }

    @Test
    void applicableOnlyOffersWhatTheModelHasFieldsFor() {
        Model names = Model.valueObject("Names")
                .field(Field.text("first"))
                .field(Field.text("last"))
                .build();
        assertThat(RuleTemplates.applicable(names)).extracting(RuleTemplate::id).containsExactly(
                "different", "at-least-one", "only-one", "exactly-one", "required-together", "required-when",
                "forbidden-when");
        Model mixedDates = Model.valueObject("Mixed")
                .field(Field.date("day"))
                .field(Field.timestamp("at"))
                .build();
        assertThat(RuleTemplates.applicable(mixedDates)).extracting(RuleTemplate::id)
                .contains("not-in-future", "not-in-past").doesNotContain("not-before", "after", "different");
        Model one = Model.valueObject("One").field(Field.model("address", "address")).build();
        assertThat(RuleTemplates.applicable(one)).isEmpty();
        assertThat(template("different").candidates(SAMPLE, "field")).extracting(Field::name).doesNotContain("tags");
        assertThat(template("at-least-one").candidates(SAMPLE, "fields")).hasSize(SAMPLE.fields().size());
    }

    @Test
    void modelFieldsCountForPresenceRules() {
        Model address = Model.valueObject("Address").field(Field.text("line").required()).build();
        Model person = Model.valueObject("Person")
                .field(Field.model("home", "address"))
                .field(Field.model("work", "address"))
                .build();
        Invariant rule = template("at-least-one").create(person, Map.of("fields", "home, work"));
        Model withRule = person.toBuilder().invariant(rule.expression(), rule.message()).build();
        Schema schema = Schema.builder().model(address).model(withRule).build();
        assertThat(RecordValues.convert(withRule, schema, Map.of("work", Map.of("line", "x")), "x").valid()).isTrue();
        assertThat(RecordValues.convert(withRule, schema, Map.of(), "x").valid()).isFalse();
    }

    @Test
    void recognizeIgnoresSpacingAndAcceptsUnguardedOrFullyGuardedForms() {
        Model range = Model.valueObject("Range").field(Field.date("from")).field(Field.date("through")).build();
        assertThat(RuleTemplates.recognize(new Invariant("value.through>=value.from", null), range))
                .hasValueSatisfying(m -> {
                    assertThat(m.template().id()).isEqualTo("not-before");
                    assertThat(m.arguments()).isEqualTo(Map.of("field", "through", "other", "from"));
                });
        assertThat(RuleTemplates.recognize(new Invariant("value.start <= today", null), SAMPLE)).isPresent();
        assertThat(RuleTemplates.recognize(
                new Invariant("!has(value.start) || value.start <= today", null), SAMPLE)).isPresent();
        assertThat(RuleTemplates.recognize(new Invariant("value.through > value.from + 1", null), range)).isEmpty();
        assertThat(RuleTemplates.recognize(new Invariant("size(value.tags) > 2", null), SAMPLE)).isEmpty();
        assertThat(RuleTemplates.recognize(new Invariant("value.nope >= value.start", null), SAMPLE)).isEmpty();
        assertThat(RuleTemplates.recognize(new Invariant("", null), SAMPLE)).isEmpty();
    }

    @Test
    void literalsAreQuotedPerType() {
        assertThat(RuleTemplate.literal(Field.text("t").build(), "a'b\\c").cel()).isEqualTo("'a\\'b\\\\c'");
        assertThat(RuleTemplate.literal(Field.decimal("d").build(), "5").cel()).isEqualTo("5.0");
        assertThat(RuleTemplate.literal(Field.decimal("d").build(), "-0.250").cel()).isEqualTo("-0.25");
        assertThat(RuleTemplate.literal(Field.integer("i").build(), " 42 ").cel()).isEqualTo("42");
        assertThat(RuleTemplate.literal(Field.bool("b").build(), "False").cel()).isEqualTo("false");
        assertThat(RuleTemplate.literal(Field.date("d").build(), "2026-10-19").cel()).isEqualTo("'2026-10-19'");
        assertThat(RuleTemplate.literal(Field.timestamp("t").build(), "2026-10-19T08:30:00Z").cel())
                .isEqualTo("'2026-10-19T08:30:00.000000000Z'");
        for (String cel : List.of("'a\\'b\\\\c'", "5.0", "-0.25", "'2026-10-19T08:30:00.000000000Z'")) {
            assertThat(Expressions.shared().check("value.x == " + cel, true)).as(cel).isEmpty();
        }
    }
}
