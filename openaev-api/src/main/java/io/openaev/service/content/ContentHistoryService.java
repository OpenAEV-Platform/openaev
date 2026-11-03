package io.openaev.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.ContentHistory;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ContentHistoryRepository;
import io.openaev.service.UserService;
import io.openaev.telemetry.metric_collectors.ContentMetricCollector;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

/** Version history of payloads and scenarios (F7): one method per action. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ContentHistoryService {

  private final ContentHistoryRepository contentHistoryRepository;
  private final UserService userService;
  private final ObjectMapper mapper;
  private final ContentMetricCollector contentMetricCollector;
  private final Optional<AuditLogger> auditLogger;

  // -- LIST --

  @Transactional(readOnly = true)
  public Page<ContentHistory> findHistory(ContentType type, String externalId, Pageable pageable) {
    return contentHistoryRepository.findAllByTypeAndExternalIdOrderByCreatedAtDesc(
        type, externalId, pageable);
  }

  /** History of these versions: a local payload has no external ID to file it under. */
  @Transactional(readOnly = true)
  public Page<ContentHistory> findHistoryOfVersions(
      ContentType type, Collection<String> versionIds, Pageable pageable) {
    return contentHistoryRepository.findAllByTypeAndEntityIdInOrderByCreatedAtDesc(
        type, versionIds, pageable);
  }

  // -- CREATE --

  /** The row is filed under the origin's external ID and version, and points to the copy. */
  public ContentHistory payloadDuplicated(Payload origin, Payload copy) {
    ContentHistory history = new ContentHistory();
    history.setType(ContentType.PAYLOAD);
    history.setExternalId(origin.getExternalId());
    history.setVersion(origin.getVersion());
    history.setAction(ContentHistory.ACTION.DUPLICATE);
    history.setEntityId(copy.getId());
    history.setUser(userService.currentUserOrNull());
    history.setTenant(copy.getTenant());
    return record(history);
  }

  /** Injects moved to this payload version (F30). */
  public ContentHistory payloadVersionChanged(Payload source, Payload target, int movedCount) {
    return save(target, ContentHistory.ACTION.VERSION_CHANGE, source.getVersion(), movedCount);
  }

  /** A variant created on a catalogue version: filed under the variant, from that version. */
  public ContentHistory payloadCustomized(Payload base, Payload variant) {
    return save(variant, ContentHistory.ACTION.CUSTOMIZE, base.getVersion());
  }

  public ContentHistory payloadOverridden(Payload previous, Payload saved) {
    return save(
        saved,
        ContentHistory.ACTION.OVERRIDE,
        previous.getVersion(),
        null,
        changes(previous, saved));
  }

  /** {@code choices}: the side taken for each block in conflict, by block name. */
  public ContentHistory payloadRebased(
      Payload previous, Payload saved, Map<String, String> choices) {
    ObjectNode details = changes(previous, saved);
    details.set("choices", mapper.valueToTree(choices));
    return save(saved, ContentHistory.ACTION.REBASE, previous.getVersion(), null, details);
  }

  /** A catalogue version a variant cannot be rebased on without a choice. */
  public ContentHistory payloadConflict(Payload variantVersion, Payload catalogVersion) {
    return save(variantVersion, ContentHistory.ACTION.CONFLICT, catalogVersion.getVersion());
  }

  /** A local payload saved by a user as a new version. */
  public ContentHistory payloadUpdated(Payload previous, Payload saved) {
    return save(
        saved, ContentHistory.ACTION.UPDATE, previous.getVersion(), null, changes(previous, saved));
  }

  /** A catalogue payload version installed (F3), by hand or automatically. */
  public ContentHistory payloadInstalled(Payload payload) {
    return payloadInstalled(payload, false);
  }

  public ContentHistory payloadInstalled(Payload payload, boolean automatic) {
    ObjectNode details = mapper.createObjectNode();
    details.put("repository", payload.getIdentity().getRepository());
    ContentHistory history = build(payload, ContentHistory.ACTION.INSTALL, null, null, details);
    history.setAutomatic(automatic);
    return record(history);
  }

  public ContentHistory payloadUninstalled(Payload payload) {
    return save(payload, ContentHistory.ACTION.UNINSTALL, null);
  }

  public ContentHistory payloadRevoked(Payload payload, String reason) {
    return save(payload, ContentHistory.ACTION.REVOKE, null, null, reasonOf(reason));
  }

  public ContentHistory payloadRevocationLifted(Payload payload) {
    return payloadRevocationLifted(payload, null);
  }

  public ContentHistory payloadRevocationLifted(Payload payload, String reason) {
    return save(payload, ContentHistory.ACTION.LIFT_REVOCATION, null, null, reasonOf(reason));
  }

  /** A catalogue scenario version installed (F11), or a new version next to an older one. */
  public ContentHistory scenarioInstalled(Scenario scenario, String fromVersion) {
    return save(scenario, ContentHistory.ACTION.INSTALL, fromVersion);
  }

  public ContentHistory scenarioUpdated(Scenario scenario, String fromVersion) {
    return save(scenario, ContentHistory.ACTION.UPDATE, fromVersion);
  }

  public ContentHistory scenarioRolledBack(Scenario scenario, String fromVersion) {
    return save(scenario, ContentHistory.ACTION.ROLLBACK, fromVersion);
  }

  public ContentHistory scenarioUninstalled(Scenario scenario) {
    return save(scenario, ContentHistory.ACTION.UNINSTALL, null);
  }

  private ContentHistory save(Scenario scenario, ContentHistory.ACTION action, String fromVersion) {
    ContentHistory history = new ContentHistory();
    history.setType(ContentType.SCENARIO);
    history.setExternalId(scenario.getCatalogId());
    history.setVersion(scenario.getVersion());
    history.setAction(action);
    history.setFromVersion(fromVersion);
    history.setEntityId(scenario.getId());
    history.setUser(userService.currentUserOrNull());
    history.setTenant(scenario.getTenant());
    return record(history);
  }

  private ContentHistory save(Payload payload, ContentHistory.ACTION action, String fromVersion) {
    return save(payload, action, fromVersion, null);
  }

  private ContentHistory save(
      Payload payload, ContentHistory.ACTION action, String fromVersion, Integer count) {
    return save(payload, action, fromVersion, count, null);
  }

  private ContentHistory save(
      Payload payload,
      ContentHistory.ACTION action,
      String fromVersion,
      Integer count,
      JsonNode details) {
    return record(build(payload, action, fromVersion, count, details));
  }

  private ObjectNode changes(Payload previous, Payload saved) {
    ObjectNode details = mapper.createObjectNode();
    details.set("changes", PayloadFieldChanges.between(mapper, previous, saved));
    return details;
  }

  /**
   * Every history row is a telemetry count once committed. It is audited here only when no API call
   * a user made already audits it: an automatic change, or one the system makes alone.
   */
  private ContentHistory record(ContentHistory history) {
    ContentHistory saved = contentHistoryRepository.save(history);
    boolean userRequest = RequestContextHolder.getRequestAttributes() != null;
    if (history.isAutomatic() || !userRequest) {
      auditLogger.ifPresent(logger -> logger.logEvent(auditEvent(saved, userRequest)));
    }
    afterCommit(
        () ->
            contentMetricCollector.addAction(
                history.getType().name(), history.getAction().name(), history.isAutomatic()));
    return saved;
  }

  private static void afterCommit(Runnable work) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      work.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            work.run();
          }
        });
  }

  private static AuditEvent auditEvent(ContentHistory history, boolean userRequest) {
    Map<String, Object> context = new HashMap<>();
    context.put("action", history.getAction().name());
    context.put("external_id", history.getExternalId());
    context.put("version", history.getVersion());
    context.put("from_version", history.getFromVersion());
    context.put("automatic", history.isAutomatic());
    if (history.getDetails() != null && history.getDetails().hasNonNull("reason")) {
      context.put("reason", history.getDetails().get("reason").asText());
    }
    return AuditEvent.builder()
        .eventType(EventType.MUTATION)
        .eventScope(
            switch (history.getAction()) {
              case INSTALL -> AuditEventScope.CREATE;
              case UNINSTALL -> AuditEventScope.DELETE;
              case DUPLICATE -> AuditEventScope.DUPLICATE;
              default -> AuditEventScope.UPDATE;
            })
        .eventStatus(EventStatus.SUCCESS)
        .resourceType(
            history.getType() == ContentType.SCENARIO
                ? ResourceType.SCENARIO
                : ResourceType.THREAT_ARSENAL)
        .resourceId(history.getEntityId())
        .message(
            "%s %s v%s: %s"
                .formatted(
                    history.getType(),
                    history.getExternalId(),
                    history.getVersion(),
                    history.getAction()))
        .contextData(context)
        .origin(userRequest ? AuditEventOrigin.REQUEST : AuditEventOrigin.SYSTEM)
        .build();
  }

  private ObjectNode reasonOf(String reason) {
    return reason == null ? null : mapper.createObjectNode().put("reason", reason);
  }

  // History rows are never updated: set every field before the single save.
  private ContentHistory build(
      Payload payload,
      ContentHistory.ACTION action,
      String fromVersion,
      Integer count,
      JsonNode details) {
    ContentHistory history = new ContentHistory();
    history.setType(ContentType.PAYLOAD);
    history.setExternalId(payload.getExternalId());
    history.setVersion(payload.getVersion());
    history.setAction(action);
    history.setFromVersion(fromVersion);
    history.setCount(count);
    history.setEntityId(payload.getId());
    history.setUser(userService.currentUserOrNull());
    history.setTenant(payload.getTenant());
    history.setDetails(details);
    return history;
  }
}
