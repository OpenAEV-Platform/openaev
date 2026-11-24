package io.openaev.service.catalog.history;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogContent;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.repository.ContentChangeRepository;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a tenant installed, uninstalled, revoked and lifted of a catalogue content, across its
 * versions.
 */
@Service
@RequiredArgsConstructor
public class ContentHistoryService {

  private final ContentChangeRepository contentChangeRepository;
  private final PayloadVersionService payloadVersionService;
  private final ScenarioService scenarioService;

  // -- LIST --

  @Transactional(readOnly = true)
  public List<ContentChange> payloadHistory(String tenantId, String actionId) {
    return history(payloadVersionService.payload(tenantId, actionId));
  }

  @Transactional(readOnly = true)
  public List<ContentChange> scenarioHistory(TxCtx ctx, String scenarioId) {
    return history(scenarioService.scenario(ctx, scenarioId));
  }

  /** Newest first; local content, which has no catalogue key, is found by its content id. */
  private List<ContentChange> history(CatalogContent content) {
    if (content.getCatalogRepository() == null) {
      return contentChangeRepository.findAllByTenantIdAndTypeAndContentIdOrderByCreatedAtDesc(
          content.getTenant().getId(), content.getCatalogType(), content.getCatalogContentId());
    }
    return contentChangeRepository
        .findAllByTenantIdAndTypeAndRepositoryAndExternalIdOrderByCreatedAtDesc(
            content.getTenant().getId(),
            content.getCatalogType(),
            content.getCatalogRepository(),
            content.getCatalogExternalId());
  }
}
