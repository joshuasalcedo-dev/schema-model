package io.joshuasalcedo.schema;

import io.joshuasalcedo.schema.Lifecycle.Phase;
import io.joshuasalcedo.schema.Lifecycle.State;
import io.joshuasalcedo.schema.Lifecycle.Transition;
import io.joshuasalcedo.schema.engine.Engine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleTest {

    static final Lifecycle FACILITY = Lifecycle.named("Facility lifecycle")
            .state(State.of("active", "Active", Phase.ACTIVE).asInitial().withColor("#16A34A").withIcon("mdi:check-circle"))
            .state(State.of("under_maintenance", "Under maintenance", Phase.ON_HOLD).withColor("#F59E0B").withIcon("mdi:wrench"))
            .state(State.of("inactive", "Inactive", Phase.INACTIVE).withColor("#64748B"))
            .state(State.of("decommissioned", "Decommissioned", Phase.RETIRED).asTerminal().withColor("#6B7280").withIcon("mdi:archive"))
            .transition(Transition.of("start_maintenance", "Start maintenance", "under_maintenance", "active").withIcon("mdi:wrench"))
            .transition(Transition.of("finish_maintenance", "Finish maintenance", "active", "under_maintenance"))
            .transition(Transition.of("deactivate", "Deactivate", "inactive", "active"))
            .transition(Transition.of("activate", "Activate", "active", "inactive"))
            .transition(Transition.of("decommission", "Decommission", "decommissioned").withColor("#DC2626")
                                .requires("decommissionReason").emits("facility.decommissioned"))
            .build();

    @Test
    void knowsWhereEachStateMayGo() {
        assertThat(FACILITY.id()).isEqualTo("facility-lifecycle");
        assertThat(FACILITY.validate()).isEmpty();
        assertThat(FACILITY.initialState().code()).isEqualTo("active");
        assertThat(FACILITY.transitionsFrom("active")).extracting(Transition::code)
                .containsExactly("start_maintenance", "deactivate", "decommission");
        assertThat(FACILITY.canMove("active", "under_maintenance")).isTrue();
        assertThat(FACILITY.canMove("inactive", "under_maintenance")).isFalse();
        assertThat(FACILITY.canMove("under_maintenance", "decommissioned")).isTrue(); // from any state that is not terminal
        assertThat(FACILITY.canMove("decommissioned", "active")).isFalse();
        assertThat(FACILITY.transitionsFrom("decommissioned")).isEmpty();
        assertThat(FACILITY.phaseOf("under_maintenance")).isEqualTo(Phase.ON_HOLD);
    }

    @Test
    void peopleRestyleStatesWithoutChangingWhatTheyMean() {
        Lifecycle mine = FACILITY.restyle("under_maintenance", "Being serviced", "#2563EB", "mdi:cog");
        State s = mine.state("under_maintenance").orElseThrow();
        assertThat(s.label()).isEqualTo("Being serviced");
        assertThat(s.color()).isEqualTo("#2563EB");
        assertThat(s.icon()).isEqualTo("mdi:cog");
        assertThat(s.phase()).isEqualTo(Phase.ON_HOLD);
        assertThat(mine.canMove("active", "under_maintenance")).isTrue();
        assertThat(mine.restyle("active", null, "", null).state("active").orElseThrow())
                .satisfies(a -> {
                    assertThat(a.color()).isEmpty();
                    assertThat(a.icon()).isEqualTo("mdi:check-circle");
                });
        Lifecycle more = mine.withState(State.of("mothballed", "Mothballed", Phase.INACTIVE).withColor("#A855F7"))
                .withTransition(Transition.of("mothball", "Mothball", "mothballed", "inactive"));
        assertThat(more.validate()).isEmpty();
        assertThat(more.canMove("inactive", "mothballed")).isTrue();
    }

    @Test
    void reportsEveryMistake() {
        Lifecycle bad = Lifecycle.named("Bad")
                .state(State.of("a", "A", Phase.ACTIVE).asInitial().withColor("green"))
                .state(State.of("b", "B", Phase.DONE).asTerminal().withIcon("Not An Icon"))
                .state(State.of("c", "C", Phase.ACTIVE))
                .state(State.of("a", "", Phase.ACTIVE))
                .transition(Transition.of("go", "Go", "b", "a"))
                .transition(Transition.of("back", "Back", "a", "b"))
                .transition(Transition.of("nowhere", "Nowhere", "x", "a"))
                .build();
        assertThat(bad.validate()).extracting(SchemaProblem::message)
                .anyMatch(m -> m.contains("must be #RRGGBB"))
                .anyMatch(m -> m.contains("must be a name such as mdi:wrench"))
                .anyMatch(m -> m.contains("'a' is used twice"))
                .anyMatch(m -> m.contains("label is required"))
                .anyMatch(m -> m.contains("'b' is terminal"))
                .anyMatch(m -> m.contains("'x' is not a state"));
        Lifecycle twoStarts = Lifecycle.named("Two").state(State.of("a", "A", Phase.NEW).asInitial())
                .state(State.of("b", "B", Phase.NEW).asInitial()).build();
        assertThat(twoStarts.validate()).extracting(SchemaProblem::message).anyMatch(m -> m.contains("exactly one must be initial"));
        Lifecycle island = Lifecycle.named("Island").state(State.of("a", "A", Phase.NEW).asInitial())
                .state(State.of("b", "B", Phase.DONE)).build();
        assertThat(island.validate()).extracting(SchemaProblem::message).anyMatch(m -> m.contains("'b' cannot be reached"));
    }

    @Test
    void theEngineKeepsLifecycles() {
        Engine engine = Engine.builder().build();
        engine.models().saveLifecycle(FACILITY);
        assertThat(engine.models().getLifecycle("facility-lifecycle")).contains(FACILITY);
        assertThat(engine.models().schema().lifecycle("facility-lifecycle")).isPresent();
        engine.models().saveLifecycle(FACILITY.restyle("active", "In service", null, null));
        assertThat(engine.models().getLifecycle("facility-lifecycle").orElseThrow().state("active").orElseThrow().label())
                .isEqualTo("In service");
        assertThatThrownBy(() -> engine.models().saveLifecycle(Lifecycle.named("Empty").build()))
                .isInstanceOf(SchemaException.class);
        engine.models().deleteLifecycle("facility-lifecycle");
        assertThat(engine.models().listLifecycles()).isEmpty();
    }
}
