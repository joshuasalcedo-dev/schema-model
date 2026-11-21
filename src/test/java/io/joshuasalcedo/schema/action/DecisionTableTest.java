package io.joshuasalcedo.schema.action;

import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.SchemaProblem;
import io.joshuasalcedo.schema.engine.Engine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionTableTest {

    private static final DecisionTable SLA = DecisionTable.named("SLA")
            .description("Response time and penalty by priority and contract type.")
            .input("priority").input("contractType").input("value", "has(facts.value) ? facts.value : 0")
            .output("responseTime").output("penalty")
            .row("'emergency'", "-", "-", "'PT4H'", "1.0")
            .row("'high'", "'comprehensive'", "-", "'PT8H'", "0.5")
            .row("'high'", "-", "> 100000", "'PT12H'", "facts.value * 0.000005")
            .row("'high'", "-", "-", "'PT24H'", "0.5")
            .row("in ['low', 'planned']", "-", "[0..1000]", "'P10D'", "0")
            .row("-", "-", "-", "'P5D'", "0")
            .build();

    private final Expressions expressions = new Expressions();

    @Test
    void cellsReadLikeASpreadsheet() {
        assertThat(DecisionTable.cellTest("-")).isEqualTo("true");
        assertThat(DecisionTable.cellTest("")).isEqualTo("true");
        assertThat(DecisionTable.cellTest("'emergency'")).isEqualTo("v == 'emergency'");
        assertThat(DecisionTable.cellTest("> 1000")).isEqualTo("v > 1000");
        assertThat(DecisionTable.cellTest("[1..10]")).isEqualTo("v >= 1 && v <= 10");
        assertThat(DecisionTable.cellTest("in ['a', 'b']")).isEqualTo("v in ['a', 'b']");
        assertThat(DecisionTable.cellTest("v > 0 && v < 5")).isEqualTo("v > 0 && v < 5");
        assertThat(DecisionTable.cellTest("'level v'")).isEqualTo("v == 'level v'");
    }

    @Test
    void theFirstMatchingRowDecides() {
        assertThat(SLA.validate()).isEmpty();
        assertThat(SLA.decide(Map.of("priority", "emergency"), expressions)).isEqualTo(Map.of("responseTime", "PT4H", "penalty", 1.0));
        assertThat(SLA.decide(Map.of("priority", "high", "contractType", "comprehensive"), expressions))
                .isEqualTo(Map.of("responseTime", "PT8H", "penalty", 0.5));
        assertThat(SLA.decide(Map.of("priority", "high", "value", 200000.0), expressions))
                .isEqualTo(Map.of("responseTime", "PT12H", "penalty", 1.0));
        assertThat(SLA.decide(Map.of("priority", "high"), expressions)).isEqualTo(Map.of("responseTime", "PT24H", "penalty", 0.5));
        assertThat(SLA.decide(Map.of("priority", "planned", "value", 500L), expressions))
                .isEqualTo(Map.of("responseTime", "P10D", "penalty", 0L));
        assertThat(SLA.decide(Map.of("priority", "planned", "value", 5000L), expressions))
                .isEqualTo(Map.of("responseTime", "P5D", "penalty", 0L));
    }

    @Test
    void uniqueRefusesOverlapsAndCollectGivesEveryMatch() {
        DecisionTable unique = DecisionTable.named("Discount").hitPolicy(DecisionTable.HitPolicy.UNIQUE)
                .input("total").output("percent")
                .row("< 1000", "0").row(">= 1000", "5").row(">= 5000", "10")
                .build();
        assertThat(unique.decide(Map.of("total", 500L), expressions)).isEqualTo(Map.of("percent", 0L));
        assertThat(unique.decide(Map.of(), expressions)).isEqualTo(Map.of());
        assertThatThrownBy(() -> unique.decide(Map.of("total", 6000L), expressions)).hasMessageContaining("rows [2, 3] all match");

        DecisionTable collect = DecisionTable.named("Checks").hitPolicy(DecisionTable.HitPolicy.COLLECT)
                .input("permit").input("hot").output("check")
                .row("true", "-", "'permit on site'").row("-", "true", "'fire watch'").row("-", "-", "'toolbox talk'")
                .build();
        assertThat(collect.decide(Map.of("permit", true, "hot", true), expressions))
                .isEqualTo(List.of(Map.of("check", "permit on site"), Map.of("check", "fire watch"), Map.of("check", "toolbox talk")));
    }

    @Test
    void reportsEveryMistake() {
        DecisionTable bad = new DecisionTable("bad", "Bad", "", DecisionTable.HitPolicy.FIRST,
                List.of(new DecisionTable.Input("a", ""), new DecisionTable.Input("a", ""), new DecisionTable.Input("2x", "")),
                List.of(new DecisionTable.Output("out")),
                List.of(new DecisionTable.Row(List.of("1"), List.of("1"), ""),
                        new DecisionTable.Row(List.of("> > 1", "-", "-"), List.of(""), "")));
        assertThat(bad.validate()).extracting(SchemaProblem::message)
                .anyMatch(m -> m.contains("used twice"))
                .anyMatch(m -> m.contains("not a name"))
                .anyMatch(m -> m.contains("has 1 cells for 3 inputs"))
                .anyMatch(m -> m.contains("'> > 1'"))
                .anyMatch(m -> m.contains("is empty"));
        assertThatThrownBy(() -> DecisionTable.named("X").input("a").output("b").row("1")).hasMessageContaining("needs 2 cells");
    }

    @Test
    void expressionsAndTheEngineCallIt() {
        Schema schema = Schema.builder().decision(SLA).build();
        assertThat(expressions.eval("DECIDE('sla', record).responseTime", Map.of("record", Map.of("priority", "emergency")), schema))
                .isEqualTo("PT4H");

        Engine engine = Engine.builder().build();
        engine.models().saveDecision(SLA);
        engine.models().createModel(Model.entity("Work order")
                .field(Field.text("number").identity().required())
                .field(Field.choice("priority", "emergency", "high", "low", "planned").required())
                .field(Field.text("responseTime"))
                .build());
        engine.defineAction(Action.named("SetSla").on("work-order")
                .set("responseTime", "DECIDE('sla', record).responseTime")
                .build());
        engine.create("work-order", Map.of("number", "WO-1", "priority", "high"));
        assertThat(engine.run("SetSla", "WO-1", Map.of()).values()).containsEntry("responseTime", "PT24H");
        assertThat(engine.models().listDecisions()).extracting(DecisionTable::id).containsExactly("sla");
        assertThatThrownBy(() -> engine.models().saveDecision(DecisionTable.named("Empty").input("a").build()))
                .hasMessageContaining("give at least one");
    }
}
