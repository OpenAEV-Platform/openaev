package io.openaev.service.catalog.history;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogContent;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.repository.ContentChangeRepository;
import io.openaev.service.payload.PayloadVersionChange;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
  public Page<PayloadHistoryEntry> payloadHistory(
      String tenantId, String actionId, SearchPaginationInput input) {
    Page<ContentChange> history = history(payloadVersionService.payload(tenantId, actionId), input);
    Map<String, PayloadVersionChange> changes =
        payloadVersionService.changesFromPrevious(
            tenantId,
            actionId,
            history.stream()
                .filter(change -> change.getAction().madeVersion())
                .map(ContentChange::getVersion)
                .toList());
    return history.map(
        change ->
            new PayloadHistoryEntry(
                change,
                Optional.of(change)
                    .filter(made -> made.getAction().madeVersion())
                    .map(made -> changes.get(made.getVersion()))));
  }

  @Transactional(readOnly = true)
  public Page<ContentChange> scenarioHistory(
      TxCtx ctx, String scenarioId, SearchPaginationInput input) {
    return history(scenarioService.scenario(ctx, scenarioId), input);
  }

  /** Newest first; local content, which has no catalogue key, is found by its content id. */
  private Page<ContentChange> history(CatalogContent content, SearchPaginationInput input) {
    Pageable page =
        PageRequest.of(input.getPage(), input.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    if (content.getCatalogRepository() == null) {
      return contentChangeRepository.findAllByTenantIdAndTypeAndContentId(
          content.getTenant().getId(),
          content.getCatalogType(),
          content.getCatalogContentId(),
          page);
    }
    return contentChangeRepository.findAllByTenantIdAndTypeAndRepositoryAndExternalId(
        content.getTenant().getId(),
        content.getCatalogType(),
        content.getCatalogRepository(),
        content.getCatalogExternalId(),
        page);
  }
}
