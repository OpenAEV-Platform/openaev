package io.openaev.service.payload_approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Command;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.PayloadApproval;
import io.openaev.database.model.PayloadApproval.ORIGIN;
import io.openaev.database.model.PayloadApprovalNotification.EVENT;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadApprovalRepository;
import io.openaev.notification.content.ContentNotificationEvents.PayloadVersionAdded;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.payload.PayloadVersionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payload version approval rules")
class PayloadApprovalServiceTest {

  @Mock private PayloadApprovalRepository payloadApprovalRepository;
  @Mock private PayloadApprovalNotificationService payloadApprovalNotificationService;
  @Mock private PayloadVersionService payloadVersionService;
  @Mock private InjectorContractRepository injectorContractRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private PayloadTrustPolicy payloadTrustPolicy = new PayloadTrustPolicy(List.of("FILIGRAN"));
  @InjectMocks private PayloadApprovalService service;

  private User approver;
  private User author;
  private PayloadIdentity identity;
  private final List<Payload> versions = new ArrayList<>();

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
    identity = new PayloadIdentity();
    identity.setId("identity-1");
    identity.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    versions.clear();
    lenient()
        .when(payloadApprovalRepository.save(any(PayloadApproval.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(payloadVersionService.findVersions(identity)).thenReturn(versions);
  }

  /** A version of the payload, added to its identity's versions. */
  private Command version(String number, PAYLOAD_APPROVAL_STATUS status) {
    Command command = new Command();
    command.setId("payload-" + number);
    command.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    command.setIdentity(identity);
    command.setVersion(number);
    command.setApprovalStatus(status);
    versions.add(command);
    return command;
  }

  private PayloadApproval lastEntry() {
    ArgumentCaptor<PayloadApproval> captor = ArgumentCaptor.forClass(PayloadApproval.class);
    verify(payloadApprovalRepository, atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("Status of a new version")
  class NewVersion {

    @Test
    @DisplayName("Given a version from a trusted source, onWrite should approve it on arrival")
    void given_trustedCatalogueVersion_should_beApproved() {
      // -- ARRANGE --
      identity.setManaged(true);
      identity.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
      Command filigran = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.onWrite(filigran, null, ORIGIN.CATALOG);

      // -- ASSERT --
      assertThat(filigran.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(lastEntry().getOrigin()).isEqualTo(ORIGIN.CATALOG);
      verify(payloadApprovalNotificationService, never()).recordPending(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName(
        "Given a catalogue version from a source that is not trusted, onWrite should leave it"
            + " pending")
    void given_untrustedCatalogueVersion_should_bePending() {
      // -- ARRANGE --
      identity.setManaged(true);
      identity.setSource(Payload.PAYLOAD_SOURCE.COMMUNITY);
      Command community = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.onWrite(community, null, ORIGIN.CATALOG);

      // -- ASSERT --
      assertThat(community.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      verify(payloadApprovalNotificationService).recordPending(community, null, false);
    }

    @Test
    @DisplayName(
        "Given a version written by an approver, onWrite should auto-approve it with the author as"
            + " approver")
    void given_approverWrite_should_autoApprove() {
      // -- ARRANGE --
      Command created = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.onWrite(created, approver, ORIGIN.CREATE);

      // -- ASSERT --
      assertThat(created.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      PayloadApproval entry = lastEntry();
      assertThat(entry.isAutomatic()).isTrue();
      assertThat(entry.getActor()).isEqualTo(approver);
    }

    @Test
    @DisplayName(
        "Given a new version by an author of an approved payload, onWrite should make it pending"
            + " and notify approvers of a new version")
    void given_authorNewVersion_should_bePendingAndNotify() {
      // -- ARRANGE --
      version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Command edited = version("1.1.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.onWrite(edited, author, ORIGIN.UPDATE);

      // -- ASSERT --
      assertThat(edited.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      verify(payloadApprovalNotificationService).recordPending(edited, author, true);
      verify(payloadVersionService).refreshLatest(identity);
    }

    @Test
    @DisplayName("Given a collector write, onWrite should leave the version pending")
    void given_collectorWrite_should_bePending() {
      // -- ARRANGE --
      Command collected = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.onWrite(collected, null, ORIGIN.COLLECTOR);

      // -- ASSERT --
      assertThat(collected.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      assertThat(lastEntry().getOrigin()).isEqualTo(ORIGIN.COLLECTOR);
    }

    @Test
    @DisplayName("Given a platform creation without a user, onWrite should approve it as system")
    void given_platformCreation_should_beApprovedAsSystem() {
      // -- ARRANGE --
      Command builtIn = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.onWrite(builtIn, null, ORIGIN.CREATE);

      // -- ASSERT --
      assertThat(builtIn.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(lastEntry().getOrigin()).isEqualTo(ORIGIN.SYSTEM);
    }

    @Test
    @DisplayName("Given a version already decided, onWrite should not decide it again")
    void given_decidedVersion_should_doNothing() {
      // -- ARRANGE --
      Command decided = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);
      when(payloadApprovalRepository.existsByPayloadId(decided.getId())).thenReturn(true);

      // -- ACT --
      service.onWrite(decided, approver, ORIGIN.CREATE);

      // -- ASSERT --
      assertThat(decided.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      verify(payloadApprovalRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("Supersession")
  class Supersession {

    @Test
    @DisplayName(
        "Given an older pending version, onWrite should supersede it, tell its author and not"
            + " notify approvers again")
    void given_olderPendingVersion_should_beSuperseded() {
      // -- ARRANGE --
      version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Command older = version("1.1.0", PAYLOAD_APPROVAL_STATUS.PENDING);
      PayloadApproval olderEntry = new PayloadApproval();
      olderEntry.setActor(author);
      when(payloadApprovalRepository.findFirstByPayloadIdOrderByCreatedAtAsc(older.getId()))
          .thenReturn(Optional.of(olderEntry));
      Command newer = version("1.2.0", PAYLOAD_APPROVAL_STATUS.PENDING);
      User otherAuthor = new User();
      otherAuthor.setId("other");
      otherAuthor.setEmail("other@example.com");

      // -- ACT --
      service.onWrite(newer, otherAuthor, ORIGIN.UPDATE);

      // -- ASSERT --
      assertThat(older.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.SUPERSEDED);
      assertThat(newer.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      verify(payloadApprovalNotificationService)
          .recordOutcome(older, EVENT.SUPERSEDED, otherAuthor, author, true, null);
      verify(payloadApprovalNotificationService, never()).recordPending(any(), any(), anyBoolean());
    }
  }

  @Nested
  @DisplayName("Decisions")
  class Decisions {

    @Test
    @DisplayName(
        "Given a pending version, approve should approve it, refresh the pointers and announce it")
    void given_pendingVersion_should_approve() {
      // -- ARRANGE --
      version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Command pending = version("1.1.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT --
      service.approve(pending, approver, "Looks safe");

      // -- ASSERT --
      assertThat(pending.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.APPROVED);
      assertThat(lastEntry().getComment()).isEqualTo("Looks safe");
      verify(payloadVersionService).refreshLatest(identity);
      verify(eventPublisher)
          .publishEvent(new PayloadVersionAdded(Tenant.DEFAULT_TENANT_UUID, pending.getId()));
    }

    @Test
    @DisplayName("Given a version that is not pending, approve should refuse it")
    void given_notPendingVersion_should_refuse() {
      // -- ARRANGE --
      Command superseded = version("1.0.0", PAYLOAD_APPROVAL_STATUS.SUPERSEDED);

      // -- ACT / ASSERT --
      assertThatThrownBy(() -> service.approve(superseded, approver, null))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Given no reason, reject should refuse")
    void given_noReason_should_refuseRejection() {
      // -- ARRANGE --
      Command pending = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT / ASSERT --
      assertThatThrownBy(() -> service.reject(pending, approver, " "))
          .isInstanceOf(BadRequestException.class);
      assertThat(pending.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
    }
  }

  @Nested
  @DisplayName("Taking a decision back")
  class TakingBack {

    @Test
    @DisplayName(
        "Given an approved version, reject should reject it, tell its author and refresh the"
            + " pointers")
    void given_approvedVersion_should_reject() {
      // -- ARRANGE --
      version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Command approved = version("1.1.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      PayloadApproval creation = new PayloadApproval();
      creation.setActor(author);
      when(payloadApprovalRepository.findFirstByPayloadIdOrderByCreatedAtAsc(approved.getId()))
          .thenReturn(Optional.of(creation));

      // -- ACT --
      service.reject(approved, approver, "Approved by mistake");

      // -- ASSERT --
      assertThat(approved.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.REJECTED);
      assertThat(lastEntry().getComment()).isEqualTo("Approved by mistake");
      verify(payloadApprovalNotificationService)
          .recordOutcome(approved, EVENT.REJECTED, approver, author, true, "Approved by mistake");
      verify(payloadVersionService).refreshLatest(identity);
    }

    @Test
    @DisplayName(
        "Given an approved version, reopen should move it back to pending without notifying"
            + " anyone")
    void given_approvedVersion_should_reopen() {
      // -- ARRANGE --
      Command approved = version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);

      // -- ACT --
      service.reopen(approved, approver, "Second review");

      // -- ASSERT --
      assertThat(approved.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      PayloadApproval entry = lastEntry();
      assertThat(entry.getOrigin()).isEqualTo(ORIGIN.REOPEN);
      assertThat(entry.isAutomatic()).isFalse();
      assertThat(entry.getComment()).isEqualTo("Second review");
      verify(payloadVersionService).refreshLatest(identity);
      verifyNoInteractions(payloadApprovalNotificationService);
    }

    @Test
    @DisplayName("Given a rejected version, reopen should move it back to pending")
    void given_rejectedVersion_should_reopen() {
      // -- ARRANGE --
      Command rejected = version("1.0.0", PAYLOAD_APPROVAL_STATUS.REJECTED);

      // -- ACT --
      service.reopen(rejected, approver, null);

      // -- ASSERT --
      assertThat(rejected.getApprovalStatus()).isEqualTo(PAYLOAD_APPROVAL_STATUS.PENDING);
      assertThat(lastEntry().getComment()).isNull();
    }

    @Test
    @DisplayName("Given a pending or superseded version, reopen should refuse it")
    void given_notDecidedVersion_should_refuseReopen() {
      // -- ARRANGE --
      Command pending = version("1.0.0", PAYLOAD_APPROVAL_STATUS.PENDING);
      Command superseded = version("1.1.0", PAYLOAD_APPROVAL_STATUS.SUPERSEDED);

      // -- ACT / ASSERT --
      assertThatThrownBy(() -> service.reopen(pending, approver, null))
          .isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> service.reopen(superseded, approver, null))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Given a rejected version, reject should refuse it")
    void given_rejectedVersion_should_refuseReject() {
      // -- ARRANGE --
      Command rejected = version("1.0.0", PAYLOAD_APPROVAL_STATUS.REJECTED);

      // -- ACT / ASSERT --
      assertThatThrownBy(() -> service.reject(rejected, approver, "Again"))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("Moving the injects of the replaced version")
  class MoveOffers {

    @Test
    @DisplayName(
        "Given an approved version, moveOffer should offer the injects of the highest approved"
            + " version below it")
    void given_approvedVersion_should_offerReplacedVersion() {
      // -- ARRANGE --
      version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Command replaced = version("1.1.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      version("1.2.0", PAYLOAD_APPROVAL_STATUS.SUPERSEDED);
      Command approved = version("1.3.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      InjectorContract replacedContract = new InjectorContract();
      replacedContract.setId("contract-1.1.0");
      when(injectorContractRepository.findInjectorContractByPayload(replaced))
          .thenReturn(Optional.of(replacedContract));
      when(payloadVersionService.countTemplateUsages(replacedContract)).thenReturn(3);

      // -- ACT --
      Optional<PayloadApprovalService.MoveOffer> offer = service.moveOffer(approved);

      // -- ASSERT --
      assertThat(offer).isPresent();
      assertThat(offer.get().from()).isEqualTo(replaced);
      assertThat(offer.get().fromContract()).isEqualTo(replacedContract);
      assertThat(offer.get().usages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Given a pending version, moveOffer should offer nothing")
    void given_pendingVersion_should_offerNothing() {
      // -- ARRANGE --
      version("1.0.0", PAYLOAD_APPROVAL_STATUS.APPROVED);
      Command pending = version("1.1.0", PAYLOAD_APPROVAL_STATUS.PENDING);

      // -- ACT / ASSERT --
      assertThat(service.moveOffer(pending)).isEmpty();
      verify(injectorContractRepository, never()).findInjectorContractByPayload(any());
    }
  }
}
