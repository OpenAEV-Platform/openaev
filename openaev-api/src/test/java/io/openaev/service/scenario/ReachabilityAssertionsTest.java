package io.openaev.service.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Command;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReachabilityAssertionsTest {

  private static Inject inject(String title, boolean readOnly, boolean enabled) {
    InjectorContract contract = new InjectorContract();
    contract.setId(UUID.randomUUID().toString());
    contract.setReadOnly(readOnly);
    Command payload = new Command(UUID.randomUUID().toString(), Command.COMMAND_TYPE, "payload");
    payload.setReadOnly(false);
    contract.setPayload(payload);
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    inject.setTitle(title);
    inject.setEnabled(enabled);
    inject.setInjectorContract(contract);
    return inject;
  }

  private static Scenario scenario(boolean assertion, Inject... injects) {
    Scenario scenario = new Scenario();
    scenario.setReachabilityAssertion(assertion);
    scenario.setInjects(Set.of(injects));
    return scenario;
  }

  @Test
  @DisplayName("Default: a scenario that is not an assertion always runs, whatever its injects")
  void given_notAnAssertion_should_alwaysRun() {
    Scenario scenario = scenario(false, inject("port scan", false, true));

    assertThat(ReachabilityAssertions.mayRun(scenario)).isTrue();
    assertThat(ReachabilityAssertions.refusal(scenario)).isNull();
  }

  @Test
  @DisplayName("An assertion of read-only injects runs")
  void given_assertionOfReadOnlyInjects_should_run() {
    Scenario scenario =
        scenario(
            true, inject("port 22 closed", true, true), inject("admin answers 403", true, true));

    assertThat(ReachabilityAssertions.mayRun(scenario)).isTrue();
    assertThat(ReachabilityAssertions.refusal(scenario)).isNull();
  }

  @Test
  @DisplayName("An assertion holding an enabled inject that is not read-only is refused, named")
  void given_assertionWithNonReadOnlyInject_should_refuseAndNameIt() {
    Scenario scenario =
        scenario(true, inject("port 22 closed", true, true), inject("exploit", false, true));

    assertThat(ReachabilityAssertions.mayRun(scenario)).isFalse();
    assertThat(ReachabilityAssertions.refusal(scenario)).contains("exploit");
    assertThat(ReachabilityAssertions.refusal(scenario)).doesNotContain("port 22 closed");
  }

  @Test
  @DisplayName("A disabled inject that is not read-only does not block the assertion")
  void given_disabledNonReadOnlyInject_should_notBlock() {
    Scenario scenario =
        scenario(true, inject("port 22 closed", true, true), inject("exploit", false, false));

    assertThat(ReachabilityAssertions.mayRun(scenario)).isTrue();
  }

  @Test
  @DisplayName("A payload marked read-only makes the inject read-only")
  void given_readOnlyPayload_should_countAsReadOnly() {
    Inject inject = inject("http check", false, true);
    inject.getInjectorContract().orElseThrow().getPayload().setReadOnly(true);

    assertThat(ReachabilityAssertions.isReadOnly(inject)).isTrue();
    assertThat(ReachabilityAssertions.mayRun(scenario(true, inject))).isTrue();
  }
}
