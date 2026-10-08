package io.openaev.service.payload_approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.openaev.database.model.Command;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.PayloadApproval;
import io.openaev.database.model.PayloadApproval.ORIGIN;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.PayloadApprovalRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.readiness.LaunchReadinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payload approval rules")
class PayloadApprovalServiceTest {

  @Mock private PayloadApprovalRepository payloadApprovalRepository;
  @Mock private LaunchReadinessService launchReadinessService;
  @InjectMocks private PayloadApprovalService service;

  private User approver;
  private User author;

  @BeforeEach
  void setUp() {
    // An admin holds every capability, "Approve content" included; a user without groups holds
    // none.
    approver = new User();
    approver.setId("approver");
    approver.setEmail("approver@example.com");
    approver.setAdmin(true);
    author = new User();
    author.setId("author");
    author.setEmail("author@example.com");
    lenient()
        .when(payloadApprovalRepository.save(any(PayloadApproval.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static Command payload(PAYLOAD_APPROVAL_STATUS status) {
    Command command = new Command();
    command.setId("payload-1");
    command.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    command.setExecutor("sh");
    command.setContent("echo hello");
    command.setApprovalStatus(status);
    return command;
  }

  private PayloadApproval lastRecorded() {
    ArgumentCaptor<PayloadApproval> captor = ArgumentCaptor.forClass(PayloadApproval.class);
    verify(payloadApprovalRepository).save(captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("Writes")
  class Writes {

    @Test
    @DisplayName("a create by an approver is auto-approved and recorded as such")
    void given_createByApprover_should_autoApprove() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.PENDING);

      service.onWrite(command, approver, ORIGIN.CREATE, null);

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(command.getApprovedFingerprint()).isEqualTo(PayloadFingerprint.of(command));
      PayloadApproval entry = lastRecorded();
      assertThat(entry.isAutomatic()).isTrue();
      assertThat(entry.getActor()).isEqualTo(approver);
      assertThat(entry.getComment()).isEqualTo(PayloadApprovalService.AUTO_APPROVED_COMMENT);
    }

    @Test
    @DisplayName("a create by an author without the capability is pending")
    void given_createByAuthor_should_bePending() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.PENDING);

      service.onWrite(command, author, ORIGIN.CREATE, null);

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      assertThat(command.getApprovedFingerprint()).isNull();
      assertThat(lastRecorded().getOrigin()).isEqualTo(ORIGIN.CREATE);
    }

    @Test
    @DisplayName("duplicates and imports follow the same rule as a create")
    void given_duplicateOrImport_should_followTheActor() {
      Command byApprover = payload(PAYLOAD_APPROVAL_STATUS.PENDING);
      Command byAuthor = payload(PAYLOAD_APPROVAL_STATUS.PENDING);

      service.onWrite(byApprover, approver, ORIGIN.DUPLICATE, null);
      service.onWrite(byAuthor, author, ORIGIN.IMPORT, null);

      assertThat(byApprover.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(byAuthor.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
    }

    @Test
    @DisplayName("an author's content edit sends an approved payload back to pending")
    void given_contentEditByAuthor_should_bePending() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.APPROVED);
      String before = PayloadFingerprint.of(command);
      command.setContent("echo changed");

      service.onWrite(command, author, ORIGIN.UPDATE, before);

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
    }

    @Test
    @DisplayName("an approver's content edit stays approved (auto-approved)")
    void given_contentEditByApprover_should_stayApproved() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.REJECTED);
      String before = PayloadFingerprint.of(command);
      command.setContent("echo fixed");

      service.onWrite(command, approver, ORIGIN.UPDATE, before);

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(lastRecorded().isAutomatic()).isTrue();
    }

    @Test
    @DisplayName("a cosmetic edit keeps the status and records nothing")
    void given_cosmeticEdit_should_keepStatus() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.APPROVED);
      String before = PayloadFingerprint.of(command);
      command.setName("Renamed");

      service.onWrite(command, author, ORIGIN.UPDATE, before);

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      verify(payloadApprovalRepository, never()).save(any());
    }

    @Test
    @DisplayName("a rejected payload edited by an author becomes pending again")
    void given_rejectedEditedByAuthor_should_bePending() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.REJECTED);
      String before = PayloadFingerprint.of(command);
      command.setContent("echo fixed");

      service.onWrite(command, author, ORIGIN.UPDATE, before);

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
    }

    @Test
    @DisplayName("a collector write is pending when new or changed, unchanged when identical")
    void given_collectorWrites_should_bePendingUnlessUnchanged() {
      Command created = payload(PAYLOAD_APPROVAL_STATUS.PENDING);
      service.onWrite(created, null, ORIGIN.COLLECTOR, null);
      assertThat(created.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);

      Command resynced = payload(PAYLOAD_APPROVAL_STATUS.APPROVED);
      service.onWrite(resynced, null, ORIGIN.COLLECTOR, PayloadFingerprint.of(resynced));
      assertThat(resynced.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);

      Command changed = payload(PAYLOAD_APPROVAL_STATUS.APPROVED);
      String before = PayloadFingerprint.of(changed);
      changed.setContent("echo new version");
      service.onWrite(changed, null, ORIGIN.COLLECTOR, before);
      assertThat(changed.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
    }

    @Test
    @DisplayName("built-in platform payloads and platform creations are approved")
    void given_systemWrites_should_beApproved() {
      Command builtIn = payload(PAYLOAD_APPROVAL_STATUS.PENDING);
      service.onWrite(builtIn, null, ORIGIN.SYSTEM, null);
      assertThat(builtIn.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);

      Command platformCreation = payload(PAYLOAD_APPROVAL_STATUS.PENDING);
      service.onWrite(platformCreation, null, ORIGIN.CREATE, null);
      assertThat(platformCreation.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
    }
  }

  @Nested
  @DisplayName("Decisions")
  class Decisions {

    @Test
    @DisplayName("approving a pending payload with the shown fingerprint approves it")
    void given_pendingAndSameFingerprint_should_approve() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.PENDING);

      PayloadApproval entry =
          service.approve(command, approver, PayloadFingerprint.of(command), "Looks safe");

      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(entry.getOrigin()).isEqualTo(ORIGIN.APPROVE);
      assertThat(entry.isAutomatic()).isFalse();
      assertThat(entry.getComment()).isEqualTo("Looks safe");
    }

    @Test
    @DisplayName("approving content that changed since it was shown is refused")
    void given_changedSinceShown_should_refuse() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.PENDING);
      String shown = PayloadFingerprint.of(command);
      command.setContent("echo swapped");

      assertThatThrownBy(() -> service.approve(command, approver, shown, null))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("changed since it was shown");
      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
    }

    @Test
    @DisplayName("only a pending payload can be approved or rejected")
    void given_notPending_should_refuseDecisions() {
      Command rejected = payload(PAYLOAD_APPROVAL_STATUS.REJECTED);
      Command approved = payload(PAYLOAD_APPROVAL_STATUS.APPROVED);

      assertThatThrownBy(
              () -> service.approve(rejected, approver, PayloadFingerprint.of(rejected), null))
          .isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> service.reject(approved, approver, "No"))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejecting requires a reason, kept in the history")
    void given_reject_should_requireReason() {
      Command command = payload(PAYLOAD_APPROVAL_STATUS.PENDING);

      assertThatThrownBy(() -> service.reject(command, approver, "  "))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("reason is required");

      PayloadApproval entry = service.reject(command, approver, "Deletes system logs");
      assertThat(command.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.REJECTED);
      assertThat(command.getApprovedFingerprint()).isNull();
      assertThat(entry.getComment()).isEqualTo("Deletes system logs");
      assertThat(entry.getActorName()).isEqualTo(approver.getNameOrEmail());
    }
  }

  @Test
  @DisplayName("canApprove is false for a missing user or a user without the capability")
  void given_users_should_resolveCanApprove() {
    assertThat(PayloadApprovalService.canApprove(null)).isFalse();
    assertThat(PayloadApprovalService.canApprove(author)).isFalse();
    assertThat(PayloadApprovalService.canApprove(approver)).isTrue();
  }

  @Test
  @DisplayName("the history entry carries the payload tenant")
  void given_write_should_recordPayloadTenant() {
    Payload command = payload(PAYLOAD_APPROVAL_STATUS.PENDING);
    service.onWrite(command, author, ORIGIN.CREATE, null);
    assertThat(lastRecorded().getTenant().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
