package io.openaev.service.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Command;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Scenario.APPROVAL_STATUS;
import io.openaev.database.model.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScenarioApprovalServiceTest {

  private final ScenarioApprovalService service = new ScenarioApprovalService();

  private static Inject inject(
      boolean contractReadOnly, boolean payloadReadOnly, AssetGroup... groups) {
    InjectorContract contract = new InjectorContract();
    contract.setId(UUID.randomUUID().toString());
    contract.setReadOnly(contractReadOnly);
    Command payload = new Command(UUID.randomUUID().toString(), Command.COMMAND_TYPE, "payload");
    payload.setReadOnly(payloadReadOnly);
    contract.setPayload(payload);
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    inject.setTitle("inject");
    inject.setInjectorContract(contract);
    inject.setDependsDuration(0L);
    inject.setAssetGroups(List.of(groups));
    return inject;
  }

  private static AssetGroup group(String id) {
    AssetGroup group = new AssetGroup();
    group.setId(id);
    group.setName(id);
    return group;
  }

  private static User user() {
    User user = new User();
    user.setId(UUID.randomUUID().toString());
    return user;
  }

  @Nested
  @DisplayName("Out of the box (safe mode off, approval off)")
  class Defaults {
    @Test
    void nothingWaitsAndNoInjectIsHeld() {
      Scenario scenario = new Scenario();
      Inject write = inject(false, false);
      ScenarioApprovalService.GateOutcome outcome =
          service.applyGate(scenario, List.of(write), false, false);
      assertThat(outcome.waits()).isFalse();
      assertThat(write.isEnabled()).isTrue();
      assertThat(scenario.getApprovalStatus()).isEqualTo(APPROVAL_STATUS.NOT_REQUIRED);
    }
  }

  @Nested
  @DisplayName("Safe mode")
  class SafeMode {
    @Test
    void readOnlyInjectsRunWithoutApproval() {
      Scenario scenario = new Scenario();
      Inject byContract = inject(true, false);
      Inject byPayload = inject(false, true);
      ScenarioApprovalService.GateOutcome outcome =
          service.applyGate(scenario, List.of(byContract, byPayload), true, true);
      assertThat(outcome.waits()).isFalse();
      assertThat(scenario.isSafeMode()).isTrue();
      assertThat(scenario.getApprovalStatus()).isEqualTo(APPROVAL_STATUS.NOT_REQUIRED);
      assertThat(byContract.isEnabled()).isTrue();
      assertThat(byPayload.isEnabled()).isTrue();
    }

    @Test
    void anInjectThatIsNotReadOnlyIsHeldAndTheScenarioWaits() {
      Scenario scenario = new Scenario();
      Inject read = inject(true, false);
      Inject write = inject(false, false);
      ScenarioApprovalService.GateOutcome outcome =
          service.applyGate(scenario, List.of(read, write), true, false);
      assertThat(outcome.waits()).isTrue();
      assertThat(outcome.heldInjects()).containsExactly(write);
      assertThat(read.isEnabled()).isTrue();
      assertThat(write.isEnabled()).isFalse();
      assertThat(scenario.getApprovalStatus()).isEqualTo(APPROVAL_STATUS.PENDING);
    }

    @Test
    void approvingReleasesTheHeldInjectsAndRecordsTheAuthor() {
      Scenario scenario = new Scenario();
      Inject read = inject(true, false);
      Inject write = inject(false, false);
      service.applyGate(scenario, List.of(read, write), true, false);
      User approver = user();
      List<Inject> released = service.approve(scenario, List.of(read, write), approver);
      assertThat(released).containsExactly(write);
      assertThat(write.isEnabled()).isTrue();
      assertThat(scenario.getApprovalStatus()).isEqualTo(APPROVAL_STATUS.APPROVED);
      assertThat(scenario.getApprovedBy()).isEqualTo(approver);
      assertThat(scenario.getApprovedAt()).isNotNull();
    }

    @Test
    void aNewInjectAfterApprovalMakesTheScenarioWaitAgain() {
      Scenario scenario = new Scenario();
      Inject write = inject(false, false);
      service.applyGate(scenario, List.of(write), true, false);
      service.approve(scenario, List.of(write), user());
      Inject newWrite = inject(false, false);
      ScenarioApprovalService.GateOutcome outcome =
          service.applyGate(scenario, List.of(write, newWrite), true, false);
      assertThat(outcome.waits()).isTrue();
      assertThat(outcome.heldInjects()).containsExactly(newWrite);
      assertThat(write.isEnabled()).isTrue();
    }

    @Test
    void anUpdateWithTheSameApprovedInjectsKeepsTheApproval() {
      Scenario scenario = new Scenario();
      Inject write = inject(false, false);
      service.applyGate(scenario, List.of(write), true, false);
      service.approve(scenario, List.of(write), user());
      ScenarioApprovalService.GateOutcome outcome =
          service.applyGate(scenario, List.of(write), true, false);
      assertThat(outcome.waits()).isFalse();
      assertThat(scenario.getApprovalStatus()).isEqualTo(APPROVAL_STATUS.APPROVED);
    }
  }

  @Nested
  @DisplayName("Approval required outside safe mode")
  class ApprovalRequired {
    @Test
    void theScenarioWaitsWithoutHoldingInjects() {
      Scenario scenario = new Scenario();
      Inject write = inject(false, false);
      ScenarioApprovalService.GateOutcome outcome =
          service.applyGate(scenario, List.of(write), false, true);
      assertThat(outcome.waits()).isTrue();
      assertThat(outcome.heldInjects()).isEmpty();
      assertThat(write.isEnabled()).isTrue();
      assertThat(scenario.isWaitingForApproval()).isTrue();
    }

    @Test
    void approvingAScenarioThatDoesNotWaitIsRefused() {
      Scenario scenario = new Scenario();
      assertThatThrownBy(() -> service.approve(scenario, List.of(), user()))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("Asset group run order")
  class RunOrder {
    @Test
    void injectsOnTheFirstGroupsStartFirst() {
      AssetGroup production = group("production");
      AssetGroup staging = group("staging");
      Inject onStaging = inject(true, false, staging);
      Inject onProduction = inject(true, false, production);
      Scenario scenario = new Scenario();
      service.applyAssetGroupOrder(
          scenario, List.of("production", "staging"), List.of(onStaging, onProduction));
      assertThat(onProduction.getDependsDuration()).isZero();
      assertThat(onStaging.getDependsDuration())
          .isEqualTo(ScenarioApprovalService.ORDER_STEP_SECONDS);
      assertThat(scenario.getAssetGroupOrder()).containsExactly("production", "staging");
    }

    @Test
    void reorderingReplacesThePreviousShiftInsteadOfAddingToIt() {
      AssetGroup a = group("a");
      AssetGroup b = group("b");
      Inject onA = inject(true, false, a);
      Inject onB = inject(true, false, b);
      Scenario scenario = new Scenario();
      service.applyAssetGroupOrder(scenario, List.of("a", "b"), List.of(onA, onB));
      service.applyAssetGroupOrder(scenario, List.of("b", "a"), List.of(onA, onB));
      assertThat(onB.getDependsDuration()).isZero();
      assertThat(onA.getDependsDuration()).isEqualTo(ScenarioApprovalService.ORDER_STEP_SECONDS);
    }

    @Test
    void anAssetGroupTheScenarioDoesNotTargetIsRefused() {
      Inject onA = inject(true, false, group("a"));
      Scenario scenario = new Scenario();
      assertThatThrownBy(
              () -> service.applyAssetGroupOrder(scenario, List.of("unknown"), List.of(onA)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown");
    }
  }
}
