package io.openaev.service.content;

import io.openaev.database.model.ContentHistory;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ContentHistoryRepository;
import io.openaev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Version history of catalogue content (F7): one method per action. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ContentHistoryService {

  private final ContentHistoryRepository contentHistoryRepository;
  private final UserService userService;

  // -- LIST --

  @Transactional(readOnly = true)
  public Page<ContentHistory> findHistory(ContentType type, String externalId, Pageable pageable) {
    return contentHistoryRepository.findAllByTypeAndExternalIdOrderByCreatedAtDesc(
        type, externalId, pageable);
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
  public ContentHistory payloadVersionChanged(Payload source, Payload target) {
    return save(target, ContentHistory.ACTION.VERSION_CHANGE, source.getVersion());
  }

  public ContentHistory payloadRevoked(Payload payload) {
    return save(payload, ContentHistory.ACTION.REVOKE, null);
  }

  public ContentHistory payloadRevocationLifted(Payload payload) {
    return save(payload, ContentHistory.ACTION.LIFT_REVOCATION, null);
  }

  /** A catalogue scenario version installed (F11), or a new version next to an older one. */
  public ContentHistory scenarioInstalled(Scenario scenario, String fromVersion) {
    return save(scenario, ContentHistory.ACTION.INSTALL, fromVersion);
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

  // History rows are never updated: set every field before the single save.
  private ContentHistory save(Payload payload, ContentHistory.ACTION action, String fromVersion) {
    ContentHistory history = new ContentHistory();
    history.setType(ContentType.PAYLOAD);
    history.setExternalId(payload.getExternalId());
    history.setVersion(payload.getVersion());
    history.setAction(action);
    history.setFromVersion(fromVersion);
    history.setEntityId(payload.getId());
    history.setUser(userService.currentUserOrNull());
    history.setTenant(payload.getTenant());
    return contentHistoryRepository.save(history);
  }
}
