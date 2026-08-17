package io.openaev.rest.finding;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingHistoryActionType;
import io.openaev.database.model.FindingTriageHistory;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingTriageHistoryRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.finding.form.FindingArchiveBulkItemOutput;
import io.openaev.service.UserService;
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
 * a simple, reversible, non-graph flag (no transition validation, no admin-only revert), unlike
 * triage status - it does, however, share the same append-only {@link FindingTriageHistory} log
 * (via {@link FindingHistoryActionType#ARCHIVE}/{@code UNARCHIVE}), so the "Triage history" tab
 * shows a single unified timeline of everything that happened to a finding.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FindingArchiveService {

  // Fixed, non-user-supplied justifications: the archive dialog collects no free-text input
  // (unlike triage), so these satisfy FindingTriageHistory#justification's NOT NULL / min-length
  // constraint without adding a justification field to the archive UX.
  private static final String ARCHIVE_JUSTIFICATION = "Archived via bulk action";
  private static final String UNARCHIVE_JUSTIFICATION = "Un-archived via bulk action";

  private final FindingRepository findingRepository;
  private final FindingTriageHistoryRepository findingTriageHistoryRepository;
  private final TenantRepository tenantRepository;
  private final UserService userService;

  @Transactional
  public List<FindingArchiveBulkItemOutput> archiveBulk(
      @NotEmpty final List<String> findingIds, @NotNull final Boolean archived) {
    String tenantId = TenantContext.getCurrentTenant();
    User currentUser = userService.currentUser();
    List<FindingArchiveBulkItemOutput> results = new ArrayList<>();
    for (String findingId : findingIds) {
      try {
        Finding finding = requireFinding(findingId, tenantId);
        Instant archivedAt = archived ? Instant.now() : null;
        finding.setArchivedAt(archivedAt);
        // Any explicit archive/un-archive action resets the soft-deletion "stasis" clock: a
        // freshly-archived finding is not stale yet, and an un-archived finding is active again -
        // see FindingSoftDeleteJob / Finding#softDeletedAt.
        finding.setSoftDeletedAt(null);
        findingRepository.save(finding);
        // An archive/un-archive change is a human action: bump humanUpdateDate ("Updated at"
        // filter), not updateDate ("Last seen", reserved for scanner detection).
        findingRepository.touchHumanUpdate(finding.getId(), tenantId);
        recordHistory(finding, archived, currentUser, tenantId);
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

  private void recordHistory(Finding finding, boolean archived, User actor, String tenantId) {
    FindingTriageHistory history = new FindingTriageHistory();
    history.setFinding(finding);
    history.setActionType(
        archived ? FindingHistoryActionType.ARCHIVE : FindingHistoryActionType.UNARCHIVE);
    // No triage status transition to describe for an archive/un-archive event - see
    // FindingTriageHistory#fromStatus/#toStatus javadoc.
    history.setJustification(archived ? ARCHIVE_JUSTIFICATION : UNARCHIVE_JUSTIFICATION);
    history.setActor(actor);
    history.setTenant(tenantRepository.getReferenceById(tenantId));
    findingTriageHistoryRepository.save(history);
  }

  private Finding requireFinding(@NotBlank String findingId, @NotBlank String tenantId) {
    return findingRepository
        .findByIdAndTenantId(findingId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Finding not found with id: " + findingId));
  }
}
