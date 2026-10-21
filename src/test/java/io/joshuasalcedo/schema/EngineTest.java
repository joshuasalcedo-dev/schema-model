package io.joshuasalcedo.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.joshuasalcedo.schema.SchemaException.Reason;
import io.joshuasalcedo.schema.action.Action;
import io.joshuasalcedo.schema.engine.Engine;
import io.joshuasalcedo.schema.record.Record;

class EngineTest {

    private final List<String> events = new ArrayList<>();
    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = Engine.builder()
                .events(e -> events.add(e.name()))
                .handler("loyalty.bonus", ctx -> {
                    long points = (Long) ctx.values().getOrDefault("points", 0L);
                    ctx.values().put("points", points + 100);
                })
                .build();
        engine.models().createModel(Model.valueObject("Address")
                                            .field(Field.text("street").required())
                                            .field(Field.text("city").required())
                                            .build());
        engine.models().createModel(Model.entity("Customer")
                                            .field(Field.text("id").identity().required())
                                            .field(Field.text("email").required().pattern(".+@.+"))
                                            .field(Field.choice("status", "active", "closed").required())
                                            .field(Field.integer("points"))
                                            .field(Field.decimal("balance"))
                                            .field(Field.date("since"))
                                            .field(Field.text("closedReason"))
                                            .field(Field.model("address", "address"))
                                            .build());
        engine.models().createModel(Model.entity("Order")
                                            .field(Field.text("number").identity().required())
                                            .field(Field.model("customer", "customer").required())
                                            .build());
    }

    private Record alice() {
        return engine.create("customer", Map.of(
                "id", "c-1", "email", "alice@example.com", "status", "active", "points", 5,
                "balance", "12.50", "since", "2026-01-02",
                "address", Map.of("street", "1 Main St", "city", "Manila")));
    }

    @Test
    void createsRecordsCheckedAgainstTheModel() {
        Record alice = alice();

        assertThat(alice.id()).isEqualTo("c-1");
        assertThat(alice.version()).isEqualTo(1);
        assertThat(alice.values()).containsEntry("points", 5L)
                .containsEntry("balance", new BigDecimal("12.50"))
                .containsEntry("since", LocalDate.of(2026, 1, 2));
        assertThat(events).containsExactly("customer.created");

        assertThatThrownBy(() -> engine.create("customer", Map.of("id", "c-2", "email", "nope", "status", "gone",
                                                                  "extra", 1)))
                .isInstanceOfSatisfying(SchemaException.class, e -> assertThat(e.problems())
                        .extracting(SchemaProblem::path)
                        .containsExactly("record.extra", "record.email", "record.status"));
        assertThatThrownBy(this::alice)
                .isInstanceOfSatisfying(SchemaException.class,
                                        e -> assertThat(e.reason()).isEqualTo(Reason.ALREADY_EXISTS));
    }

    @Test
    void updatesWithVersionsAndKeepsReferencesIntact() {
        alice();
        Map<String, Object> changes = new HashMap<>();
        changes.put("points", 7);
        changes.put("balance", null);
        Record updated = engine.update("customer", "c-1", changes, 1);
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.values()).containsEntry("points", 7L).doesNotContainKey("balance");

        assertThatThrownBy(() -> engine.update("customer", "c-1", Map.of("points", 8), 1))
                .isInstanceOfSatisfying(SchemaException.class, e -> assertThat(e.reason()).isEqualTo(Reason.CONFLICT));
        assertThatThrownBy(() -> engine.update("customer", "c-1", Map.of("id", "c-9"), 0))
                .isInstanceOf(SchemaException.class).hasMessageContaining("cannot change");

        assertThatThrownBy(() -> engine.create("order", Map.of("number", "o-1", "customer", "c-404")))
                .hasMessageContaining("customer/c-404 does not exist");
        engine.create("order", Map.of("number", "o-1", "customer", "c-1"));
        assertThatThrownBy(() -> engine.delete("customer", "c-1"))
                .isInstanceOfSatisfying(SchemaException.class, e -> assertThat(e.reason()).isEqualTo(Reason.IN_USE));
        assertThatThrownBy(() -> engine.models().deleteModel("customer"))
                .isInstanceOfSatisfying(SchemaException.class, e -> assertThat(e.reason()).isEqualTo(Reason.IN_USE));

        engine.delete("order", "o-1");
        engine.delete("customer", "c-1");
        assertThat(engine.list("customer")).isEmpty();
    }

    @Test
    void runsActionsDefinedAsData() {
        alice();
        engine.defineAction(Action.named("CloseAccount")
                                    .on("customer")
                                    .input(Field.text("reason").required())
                                    .require("record.status == 'active'", "only active accounts can be closed")
                                    .set("status", "'closed'")
                                    .set("closedReason", "input.reason")
                                    .set("points", "has(record.points) ? record.points * 2 : 0")
                                    .call("loyalty.bonus")
                                    .emit("customer.closed")
                                    .build());
        events.clear();

        Record closed = engine.run("CloseAccount", "c-1", Map.of("reason", "moved away"));

        assertThat(closed.values()).containsEntry("status", "closed")
                .containsEntry("closedReason", "moved away")
                .containsEntry("points", 110L);
        assertThat(closed.version()).isEqualTo(2);
        assertThat(events).containsExactly("customer.updated", "customer.closed");

        assertThatThrownBy(() -> engine.run("CloseAccount", "c-1", Map.of("reason", "again")))
                .isInstanceOfSatisfying(SchemaException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.RULE_FAILED);
                    assertThat(e).hasMessage("only active accounts can be closed");
                });
        assertThatThrownBy(() -> engine.run("CloseAccount", "c-1", Map.of()))
                .hasMessageContaining("input.reason is required");
    }

    @Test
    void actionsReadTheRecordsTheyPointTo() {
        alice();
        engine.defineAction(Action.named("PlaceOrder")
                                    .creates("order")
                                    .input(Field.model("customer", "customer").required())
                                    .require("linked.customer.status == 'active'", "only active customers order")
                                    .set("customer", "input.customer")
                                    .set("number", "'o-' + linked.customer.id + '-' + string(linked.customer.points)")
                                    .build());
        Record order = engine.run("PlaceOrder", null, Map.of("customer", "c-1"));
        assertThat(order.values()).containsEntry("number", "o-c-1-5");

        // on an existing record: what the record points to
        engine.defineAction(Action.named("Ship").on("order")
                                    .require("linked.customer.status == 'active'", "the customer's account is closed")
                                    .set("customer", "record.customer")
                                    .build());
        assertThat(engine.run("Ship", order.id(), Map.of()).values()).containsEntry("customer", "c-1");

        engine.update("customer", "c-1", Map.of("status", "closed", "closedReason", "moved"), 0);
        assertThatThrownBy(() -> engine.run("Ship", order.id(), Map.of()))
                .hasMessageContaining("the customer's account is closed");
        assertThatThrownBy(() -> engine.run("PlaceOrder", null, Map.of("customer", "c-1")))
                .hasMessageContaining("only active customers order");
    }

    @Test
    void creatingActionsAndBadDefinitions() {
        engine.defineAction(Action.named("SignUp")
                                    .creates("customer")
                                    .input(Field.text("id").required())
                                    .input(Field.text("email").required())
                                    .require("input.email.endsWith('@example.com')", "company addresses only")
                                    .set("id", "input.id")
                                    .set("email", "input.email")
                                    .set("status", "'active'")
                                    .set("points", "0")
                                    .build());

        Record bob = engine.run("SignUp", null, Map.of("id", "c-2", "email", "bob@example.com"));
        assertThat(bob.key()).isEqualTo("customer/c-2");
        assertThat(bob.values()).containsEntry("points", 0L);

        assertThatThrownBy(() -> engine.defineAction(Action.named("Broken")
                                                             .on("address")
                                                             .require("record.status ==")
                                                             .set("nope", "1")
                                                             .call("missing.handler")
                                                             .build()))
                .isInstanceOfSatisfying(SchemaException.class, e -> assertThat(e.problems())
                        .extracting(SchemaProblem::path)
                        .containsExactly("action.model", "action.rules[0]", "action.steps[0].field",
                                         "action.steps[1].handler"));
        assertThatThrownBy(() -> engine.defineAction(Action.named("Rename").on("customer")
                                                             .set("id", "'x'").build()))
                .hasMessageContaining("part of the identity");
    }
}
