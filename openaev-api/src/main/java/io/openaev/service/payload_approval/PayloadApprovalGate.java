package io.openaev.service.payload_approval;

import static io.openaev.aop.audit_log.AuditEventOrigin.REQUEST;

import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.ResourceType;
import io.openaev.service.payload_approval.BlockedPayloadsException.BlockedPayload;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The one approval check of every path that uses or runs a payload: picking it for an inject,
 * launching an atomic testing, a scenario or a simulation, a scheduled run, and the executor right
 * before dispatch.
 *
 * <p>A payload runs only when it is {@code APPROVED} and its executable content is still the one
 * that was approved (same {@link PayloadFingerprint}). Payload-less built-in actions and payloads
 * exempted by a {@link PayloadApprovalExemption} always run. A payload approved before fingerprints
 * were recorded (no approved fingerprint) is trusted as approved: any later content change makes it
 * pending again through the payload write paths.
 */
@Service
@RequiredArgsConstructor
public class PayloadApprovalGate {

  static final String PENDING_REASON = "pending approval";
  static final String REJECTED_REASON = "rejected";
  static final String CHANGED_REASON = "content changed since its approval";

  private final ObjectProvider<PayloadApprovalExemption> exemptions;
  // Conditional bean (audit transports): absent when audit logging is disabled.
  private final Optional<AuditLogger> auditLogger;

  /** Why this payload cannot run, or empty when it can (or when there is no payload). */
  public Optional<BlockedPayload> check(@Nullable final Payload payload) {
    if (payload == null) {
      return Optional.empty();
    }
    Payload unproxied = (Payload) Hibernate.unproxy(payload);
    if (exemptions.stream().anyMatch(exemption -> exemption.isExempt(unproxied))) {
      return Optional.empty();
    }
    PAYLOAD_APPROVAL_STATUS status = unproxied.getApprovalStatus();
    if (status == PAYLOAD_APPROVAL_STATUS.PENDING) {
      return Optional.of(blocked(unproxied, PENDING_REASON));
    }
    if (status == PAYLOAD_APPROVAL_STATUS.REJECTED) {
      return Optional.of(blocked(unproxied, REJECTED_REASON));
    }
    String approved = unproxied.getApprovedFingerprint();
    if (approved != null && !approved.equals(PayloadFingerprint.of(unproxied))) {
      return Optional.of(blocked(unproxied, CHANGED_REASON));
    }
    return Optional.empty();
  }

  /** Whether this payload can run (no payload counts as runnable). */
  public boolean isRunnable(@Nullable final Payload payload) {
    return check(payload).isEmpty();
  }

  /** The payloads of these injects that cannot run, each listed once, in inject order. */
  public List<BlockedPayload> blockedPayloads(@NotNull final Collection<Inject> injects) {
    Map<String, BlockedPayload> blocked = new LinkedHashMap<>();
    injects.stream()
        .filter(Objects::nonNull)
        .map(inject -> inject.getInjectorContract().map(InjectorContract::getPayload).orElse(null))
        .filter(Objects::nonNull)
        .forEach(payload -> check(payload).ifPresent(b -> blocked.putIfAbsent(b.payloadId(), b)));
    return List.copyOf(blocked.values());
  }

  /**
   * Refuses a user operation when one of these injects uses a payload that cannot run, and writes
   * the refusal to the audit log.
   *
   * @param operation what is being done, for the message (e.g. "Launching this scenario")
   * @param resourceType the resource the operation is about (scenario, simulation, inject...)
   * @param resourceId its id, when it exists already
   * @throws BlockedPayloadsException listing every blocking payload (HTTP 400)
   */
  public void requireApproved(
      @NotNull final String operation,
      final Collection<Inject> injects,
      @NotNull final ResourceType resourceType,
      @Nullable final String resourceId) {
    if (injects == null) {
      return;
    }
    List<BlockedPayload> blocked = blockedPayloads(injects);
    if (!blocked.isEmpty()) {
      auditBlocked(operation, blocked, resourceType, resourceId, REQUEST);
      throw new BlockedPayloadsException(operation, blocked);
    }
  }

  /**
   * Refuses a user operation (e.g. picking an action for an inject) when its payload cannot run.
   */
  public void requireApproved(
      @NotNull final String operation,
      @Nullable final Payload payload,
      @NotNull final ResourceType resourceType,
      @Nullable final String resourceId) {
    Optional<BlockedPayload> blocked = check(payload);
    if (blocked.isPresent()) {
      auditBlocked(operation, List.of(blocked.get()), resourceType, resourceId, REQUEST);
      throw new BlockedPayloadsException(operation, List.of(blocked.get()));
    }
  }

  /** Writes a refused operation to the audit log (BRIEF rule 7: blocked attempts are logged). */
  public void auditBlocked(
      @NotNull final String operation,
      @NotNull final List<BlockedPayload> blocked,
      @NotNull final ResourceType resourceType,
      @Nullable final String resourceId,
      @NotNull final AuditEventOrigin origin) {
    auditLogger.ifPresent(
        logger -> {
          Map<String, Object> context = new LinkedHashMap<>();
          context.put(
              "blocked_payloads",
              blocked.stream()
                  .map(
                      b -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("payload_id", b.payloadId());
                        entry.put("payload_name", b.payloadName());
                        entry.put("reason", b.reason());
                        return entry;
                      })
                  .toList());
          logger.logEvent(
              AuditEvent.builder()
                  .eventType(EventType.EXECUTION)
                  .eventScope(AuditEventScope.EXECUTION_BLOCKED_BY_APPROVAL)
                  .eventStatus(EventStatus.WARNING)
                  .resourceType(resourceType)
                  .resourceId(resourceId)
                  .message(BlockedPayloadsException.message(operation, blocked))
                  .contextData(context)
                  .origin(origin)
                  .build());
        });
  }

  private static BlockedPayload blocked(Payload payload, String reason) {
    return new BlockedPayload(payload.getId(), payload.getName(), reason);
  }
}
