package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Command;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageScenarioReadOnlyFirstTest {

  private static Inject inject(boolean contractReadOnly, boolean payloadReadOnly) {
    InjectorContract contract = new InjectorContract();
    contract.setId(UUID.randomUUID().toString());
    contract.setReadOnly(contractReadOnly);
    Command payload = new Command(UUID.randomUUID().toString(), Command.COMMAND_TYPE, "payload");
    payload.setReadOnly(payloadReadOnly);
    contract.setPayload(payload);
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    inject.setInjectorContract(contract);
    return inject;
  }

  private static Scenario scheduledScenario() {
    Scenario scenario = new Scenario();
    scenario.setRecurrence("P1D");
    scenario.setRecurrenceStart(Instant.now());
    scenario.setRecurrenceEnd(Instant.now().plusSeconds(3600));
    return scenario;
  }

  @Test
  @DisplayName("A contract or a payload marked read-only makes the inject read-only")
  void readOnlyFromContractOrPayload() {
    assertThat(CoverageScenarioReadOnlyFirst.isReadOnly(inject(true, false))).isTrue();
    assertThat(CoverageScenarioReadOnlyFirst.isReadOnly(inject(false, true))).isTrue();
    assertThat(CoverageScenarioReadOnlyFirst.isReadOnly(inject(false, false))).isFalse();
  }

  @Test
  @DisplayName("Only read-only injects: everything enabled, schedule kept")
  void onlyReadOnlyInjectsKeepTheSchedule() {
    Scenario scenario = scheduledScenario();
    Inject read = inject(true, false);
    List<Inject> held = CoverageScenarioReadOnlyFirst.apply(scenario, List.of(read));
    assertThat(held).isEmpty();
    assertThat(read.isEnabled()).isTrue();
    assertThat(scenario.getRecurrence()).isEqualTo("P1D");
    assertThat(scenario.getRecurrenceStart()).isNotNull();
  }

  @Test
  @DisplayName("An inject that is not read-only is disabled and the schedule is left unset")
  void nonReadOnlyInjectHoldsTheSchedule() {
    Scenario scenario = scheduledScenario();
    Inject read = inject(false, true);
    Inject write = inject(false, false);
    List<Inject> held = CoverageScenarioReadOnlyFirst.apply(scenario, List.of(read, write));
    assertThat(held).containsExactly(write);
    assertThat(read.isEnabled()).isTrue();
    assertThat(write.isEnabled()).isFalse();
    assertThat(scenario.getRecurrence()).isNull();
    assertThat(scenario.getRecurrenceStart()).isNull();
    assertThat(scenario.getRecurrenceEnd()).isNull();
  }

  @Test
  @DisplayName("A scenario a user already scheduled is left as the user left it")
  void userScheduledScenarioIsLeftAlone() {
    Scenario scenario = scheduledScenario();
    User user = new User();
    user.setId(UUID.randomUUID().toString());
    scenario.setScheduledBy(user);
    Inject write = inject(false, false);
    List<Inject> held = CoverageScenarioReadOnlyFirst.apply(scenario, List.of(write));
    assertThat(held).isEmpty();
    assertThat(write.isEnabled()).isTrue();
    assertThat(scenario.getRecurrence()).isEqualTo("P1D");
  }
}
