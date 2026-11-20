package io.openaev.service.catalog.history;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogContent;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.repository.CatalogHistoryRepository;
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
public class CatalogHistoryService {

  private final CatalogHistoryRepository catalogHistoryRepository;
  private final PayloadVersionService payloadVersionService;
  private final ScenarioService scenarioService;

  // -- LIST --

  @Transactional(readOnly = true)
  public List<CatalogHistory> payloadHistory(String tenantId, String actionId) {
    return history(payloadVersionService.payload(tenantId, actionId));
  }

  @Transactional(readOnly = true)
  public List<CatalogHistory> scenarioHistory(TxCtx ctx, String scenarioId) {
    return history(scenarioService.scenario(ctx, scenarioId));
  }

  /** Newest first; empty for content that does not come from the catalogue. */
  private List<CatalogHistory> history(CatalogContent content) {
    if (content.getCatalogRepository() == null) {
      return List.of();
    }
    return catalogHistoryRepository
        .findAllByTenantIdAndTypeAndRepositoryAndExternalIdOrderByCreatedAtDesc(
            content.getTenant().getId(),
            content.getCatalogType(),
            content.getCatalogRepository(),
            content.getCatalogExternalId());
  }
}
