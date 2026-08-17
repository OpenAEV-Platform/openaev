package io.openaev.rest.finding;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Finding;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.finding.form.FindingArchiveBulkItemOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for the manual "Archive" / "Un-archive" flag on {@link Finding} ({@code
 * finding_archived_at}). Deliberately kept separate from {@link FindingTriageService}: archiving is
 * a simple, reversible, non-graph flag (no transition validation, no history, no admin-only
 * revert), unlike triage status.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FindingArchiveService {

  private final FindingRepository findingRepository;

  @Transactional
  public List<FindingArchiveBulkItemOutput> archiveBulk(
      @NotEmpty final List<String> findingIds, @NotNull final Boolean archived) {
    String tenantId = TenantContext.getCurrentTenant();
    List<FindingArchiveBulkItemOutput> results = new ArrayList<>();
    for (String findingId : findingIds) {
      try {
        Finding finding = requireFinding(findingId, tenantId);
        Instant archivedAt = archived ? Instant.now() : null;
        finding.setArchivedAt(archivedAt);
        findingRepository.save(finding);
        // An archive/un-archive change is a human action: bump humanUpdateDate ("Updated at"
        // filter), not updateDate ("Last seen", reserved for scanner detection).
        findingRepository.touchHumanUpdate(finding.getId(), tenantId);
        results.add(
            FindingArchiveBulkItemOutput.builder()
                .findingId(findingId)
                .success(true)
                .archivedAt(archivedAt)
                .build());
      } catch (Exception e) {
        results.add(
            FindingArchiveBulkItemOutput.builder()
                .findingId(findingId)
                .success(false)
                .error(e.getMessage())
                .build());
      }
    }
    return results;
  }

  private Finding requireFinding(@NotBlank String findingId, @NotBlank String tenantId) {
    return findingRepository
        .findByIdAndTenantId(findingId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Finding not found with id: " + findingId));
  }
}
