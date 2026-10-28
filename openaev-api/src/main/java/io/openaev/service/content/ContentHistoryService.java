package io.openaev.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContentHistory;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ContentHistoryRepository;
import io.openaev.service.UserService;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Version history of payloads and scenarios (F7): one method per action. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ContentHistoryService {

  private final ContentHistoryRepository contentHistoryRepository;
  private final UserService userService;
  private final ObjectMapper mapper;

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
    return contentHistoryRepository.save(history);
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
    return contentHistoryRepository.save(history);
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
    return contentHistoryRepository.save(history);
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
    return contentHistoryRepository.save(build(payload, action, fromVersion, count, details));
  }

  private ObjectNode changes(Payload previous, Payload saved) {
    ObjectNode details = mapper.createObjectNode();
    details.set("changes", PayloadFieldChanges.between(mapper, previous, saved));
    return details;
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
