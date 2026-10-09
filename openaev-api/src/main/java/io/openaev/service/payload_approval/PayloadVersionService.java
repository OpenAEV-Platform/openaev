package io.openaev.service.payload_approval;

import static io.openaev.service.payload_approval.PayloadApprovalService.blankToNull;
import static io.openaev.service.payload_approval.PayloadApprovalService.bounded;
import static java.time.Instant.now;

import io.openaev.database.model.Document;
import io.openaev.database.model.Executable;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.PayloadExecutableContent;
import io.openaev.database.model.PayloadVersion;
import io.openaev.database.model.PayloadVersion.ORIGIN;
import io.openaev.database.model.PayloadVersion.STATUS;
import io.openaev.database.model.User;
import io.openaev.database.repository.PayloadVersionRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.BadRequestException;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Versions of the executable content of approved payloads.
 *
 * <p>The payload row always holds its active, approved content, so everything that runs it
 * (executor, implants, injectors, pickers, launch checks) is unaffected by a pending edit:
 *
 * <ul>
 *   <li>An executable edit of an APPROVED payload by a user without "Approve content", or by a
 *       collector, is held back as a PENDING version; the payload keeps its approved content, and
 *       the cosmetic part of the edit applies directly.
 *   <li>One PENDING version per payload: a new held-back edit supersedes the previous one, an
 *       identical one changes nothing.
 *   <li>An executable edit by a holder of "Approve content" applies directly as a new APPROVED
 *       version and supersedes the pending one.
 *   <li>Approving a version writes its content to the payload; rejecting it keeps the payload as
 *       is.
 * </ul>
 *
 * <p>Payloads with no approved version (new, pending, rejected) are not versioned: they are edited
 * in place, as decided by {@link PayloadApprovalService}.
 */
@Service
@RequiredArgsConstructor
public class PayloadVersionService {

  private final PayloadVersionRepository payloadVersionRepository;
  private final PayloadApprovalService payloadApprovalService;
  private final DocumentService documentService;

  /** The executable content of a payload before an edit, to restore it when held back. */
  public record Active(
      PayloadExecutableContent content, @Nullable Document file, String fingerprint) {

    public static Active of(@NotNull final Payload payload) {
      Payload p = (Payload) Hibernate.unproxy(payload);
      Document file =
          switch (p) {
            case Executable executable -> executable.getExecutableFile();
            case FileDrop fileDrop -> fileDrop.getFileDropFile();
            default -> null;
          };
      return new Active(PayloadExecutableContent.of(p), file, PayloadFingerprint.of(p));
    }
  }

  /**
   * Called once an edit is applied to the (managed) payload in memory, before it is saved. Holds
   * the executable part of the edit back as a pending version when it needs approval: the payload
   * is then restored to its active content, and only the cosmetic part of the edit is saved.
   *
   * @param payload the edited payload
   * @param before the active content, captured before the edit
   * @param actor the user who edited it, {@code null} for a collector
   * @param origin what edited it
   * @return {@code true} when the edit was held back as a pending version
   */
  public boolean onEdit(
      @NotNull final Payload payload,
      @NotNull final Active before,
      @Nullable final User actor,
      @NotNull final ORIGIN origin) {
    if (payload.getId() == null
        || payload.getApprovalStatus() != PAYLOAD_APPROVAL_STATUS.APPROVED) {
      return false;
    }
    String fingerprint = PayloadFingerprint.of(payload);
    if (fingerprint.equals(before.fingerprint())) {
      // Cosmetic edit, or the active content re-sent: a pending version is left as is.
      return false;
    }
    if (origin == ORIGIN.UPDATE && PayloadApprovalService.canApprove(actor)) {
      // Applied directly (auto-approved by PayloadApprovalService) as a new approved version.
      supersedePending(payload, actor);
      PayloadVersion applied = newVersion(payload, origin, fingerprint, actor);
      applied.setStatus(STATUS.APPROVED);
      applied.setDecider(actor);
      applied.setDeciderName(actor.getNameOrEmail());
      applied.setComment(PayloadApprovalService.AUTO_APPROVED_COMMENT);
      applied.setDecidedAt(now());
      payloadVersionRepository.save(applied);
      return false;
    }

    PayloadExecutableContent edited = PayloadExecutableContent.of(payload);
    before.content().applyTo(payload, before.file());
    Optional<PayloadVersion> pending = pending(payload.getId());
    if (pending.isPresent() && pending.get().getFingerprint().equals(fingerprint)) {
      // The same content is already waiting for approval (e.g. a collector re-sync).
      return true;
    }
    supersedePending(payload, actor);
    PayloadVersion version = newVersion(payload, origin, fingerprint, actor);
    version.setSnapshot(edited);
    version.setStatus(STATUS.PENDING);
    payloadVersionRepository.save(version);
    payload.setPendingVersion(true);
    return true;
  }

  /**
   * Approves the pending version of a payload, provided it is still the one the approver saw, and
   * writes its content to the payload. The caller re-synchronises the injector contract.
   *
   * @param payload the payload, loaded with a row lock
   * @param shownFingerprint the fingerprint of the pending version displayed to the approver
   */
  public PayloadVersion approve(
      @NotNull final Payload payload,
      @NotNull final User decider,
      @NotNull final String shownFingerprint,
      @Nullable final String comment) {
    PayloadVersion version = requirePending(payload);
    if (!version.getFingerprint().equals(shownFingerprint)) {
      throw new BadRequestException(
          "The pending version of this payload changed since it was shown. Review it again, then"
              + " approve.");
    }
    String boundedComment = bounded(blankToNull(comment));
    PayloadExecutableContent snapshot = version.getSnapshot();
    Document file = snapshot.fileId() != null ? documentService.document(snapshot.fileId()) : null;
    snapshot.applyTo(payload, file);
    if (!PayloadFingerprint.of(payload).equals(version.getFingerprint())) {
      throw new BadRequestException(
          "This version can no longer be applied as it was submitted (a file it uses changed)."
              + " Edit the payload again to submit a new version.");
    }
    payload.setPendingVersion(false);
    payload.setLastModifiedBy(version.getAuthor());
    decide(version, STATUS.APPROVED, decider, boundedComment);
    payloadApprovalService.recordVersionApproval(payload, decider, boundedComment, version);
    return version;
  }

  /** Rejects the pending version of a payload: the active version stays. A reason is mandatory. */
  public PayloadVersion reject(
      @NotNull final Payload payload, @NotNull final User decider, @Nullable final String reason) {
    PayloadVersion version = requirePending(payload);
    String trimmed = blankToNull(reason);
    if (trimmed == null) {
      throw new BadRequestException("A reason is required to reject a version.");
    }
    payload.setPendingVersion(false);
    return decide(version, STATUS.REJECTED, decider, bounded(trimmed));
  }

  /** The pending version of a payload, if any. */
  public Optional<PayloadVersion> pending(@NotNull final String payloadId) {
    return payloadVersionRepository.findFirstByPayloadIdAndStatus(payloadId, STATUS.PENDING);
  }

  /** The number of the active version of a payload (1 until a later version is approved). */
  public int activeNumber(@NotNull final String payloadId) {
    return payloadVersionRepository.activeNumber(payloadId);
  }

  /**
   * The stored versions of a payload, newest first (version 1 is the payload's initial content).
   */
  public List<PayloadVersion> versions(@NotNull final String payloadId) {
    return payloadVersionRepository.findByPayloadIdOrderByNumberDesc(payloadId);
  }

  private PayloadVersion requirePending(Payload payload) {
    return pending(payload.getId())
        .orElseThrow(() -> new BadRequestException("This payload has no pending version."));
  }

  private void supersedePending(Payload payload, @Nullable User actor) {
    pending(payload.getId())
        .ifPresent(
            previous -> {
              decide(previous, STATUS.SUPERSEDED, actor, null);
              // Flushed now: Hibernate inserts before it updates, and the new pending version
              // would hit the one-pending-version-per-payload index.
              payloadVersionRepository.flush();
            });
    payload.setPendingVersion(false);
  }

  private PayloadVersion newVersion(
      Payload payload, ORIGIN origin, String fingerprint, @Nullable User author) {
    PayloadVersion version = new PayloadVersion();
    version.setTenant(payload.getTenant());
    version.setPayload(payload);
    version.setNumber(payloadVersionRepository.maxNumber(payload.getId()) + 1);
    version.setOrigin(origin);
    version.setSnapshot(PayloadExecutableContent.of(payload));
    version.setFingerprint(fingerprint);
    version.setAuthor(author);
    version.setAuthorName(author != null ? author.getNameOrEmail() : null);
    return version;
  }

  private PayloadVersion decide(
      PayloadVersion version, STATUS status, @Nullable User decider, @Nullable String comment) {
    version.setStatus(status);
    version.setDecider(decider);
    version.setDeciderName(decider != null ? decider.getNameOrEmail() : null);
    version.setComment(comment);
    version.setDecidedAt(now());
    return payloadVersionRepository.save(version);
  }
}
