package io.openaev.rest.finding;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageHistory;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingTriageHistoryRepository;
import io.openaev.database.repository.FindingTriageRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.finding.form.FindingTriageBulkItemOutput;
import io.openaev.rest.finding.form.FindingTriageHistoryOutput;
import io.openaev.rest.finding.form.FindingTriageOutput;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for {@link FindingTriage} (current status) and {@link FindingTriageHistory} (append-only
 * transitions). Deliberately depends on {@link FindingRepository} directly (not {@link
 * FindingService}) to avoid a circular dependency, since {@code FindingService} needs to be able to
 * call into this service.
 *
 * <p><b>Re-detection auto-reset status:</b> both reset hooks (injector-path {@code
 * resetTriageForReDetectedFindings(List)} and agent-path {@code
 * resetTriageForReDetectedFinding(String, String)}) have been removed. Both relied on {@code
 * finding_inject_id} as part of the natural key to detect "re-detection", but {@code inject_id} is
 * never stable across an atomic relaunch ({@code InjectService#doRelaunch}) or a new simulation
 * instantiated from a scenario ({@code ScenarioToExerciseService#toExercise}) - both mint a
 * brand-new {@code Inject} row with a new id and no lineage back to the original. So the natural
 * key can only ever repeat within a single still-open execution, never across a genuinely later
 * run, making both methods' core premise a structural no-op (or worse - liable to reset triage on
 * ordinary in-execution reporting). Re-detection auto-reset is deferred until a real, stable
 * cross-run identity key is found (no such field currently exists on {@code Inject} - unlike {@code
 * Workflow}'s {@code workflow_template_id} precedent).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FindingTriageService {

  // Explicit transition graph. Any -> UNTRIAGED (revert) is handled separately below since it
  // applies uniformly from every non-UNTRIAGED status and is gated by an additional Admin-only
  // check, not a plain capability check.
  private static final Map<FindingTriageStatus, Set<FindingTriageStatus>> ALLOWED_TRANSITIONS =
      new EnumMap<>(FindingTriageStatus.class);

  static {
    ALLOWED_TRANSITIONS.put(
        FindingTriageStatus.UNTRIAGED,
        Set.of(FindingTriageStatus.CONFIRMED, FindingTriageStatus.FALSE_POSITIVE));
    ALLOWED_TRANSITIONS.put(
        FindingTriageStatus.CONFIRMED,
        Set.of(FindingTriageStatus.RISK_ACCEPTED, FindingTriageStatus.FALSE_POSITIVE));
    ALLOWED_TRANSITIONS.put(FindingTriageStatus.FALSE_POSITIVE, Set.of());
    ALLOWED_TRANSITIONS.put(FindingTriageStatus.RISK_ACCEPTED, Set.of());
  }

  private final FindingRepository findingRepository;
  private final FindingTriageRepository findingTriageRepository;
  private final FindingTriageHistoryRepository findingTriageHistoryRepository;
  private final TenantRepository tenantRepository;
  private final UserService userService;

  // -- HTTP-driven single/bulk/history (ambient TenantContext, set by TenantInterceptor) --

  /**
   * Current triage status for a single finding, for the detail page. Deliberately does NOT fall
   * back to {@link #getOrCreateTriage}: that helper persists a new row on first access, which would
   * turn this read into a write (see backend.instructions.md - never mutate a managed entity in a
   * read path). A finding with no {@link FindingTriage} row yet has simply never been triaged, so a
   * virtual UNTRIAGED default is returned without persisting anything.
   */
  @Transactional(readOnly = true)
  public FindingTriageOutput getCurrentStatus(@NotBlank final String findingId) {
    String tenantId = TenantContext.getCurrentTenant();
    requireFinding(findingId, tenantId);
    return findingTriageRepository
        .findByFinding_Id(findingId)
        .map(FindingTriageOutput::from)
        .orElseGet(
            () ->
                FindingTriageOutput.builder()
                    .findingId(findingId)
                    .status(FindingTriageStatus.UNTRIAGED)
                    .build());
  }

  @Transactional
  public FindingTriageOutput triage(
      @NotBlank final String findingId,
      @NotNull final FindingTriageStatus targetStatus,
      @NotBlank final String justification) {
    String tenantId = TenantContext.getCurrentTenant();
    User currentUser = userService.currentUser();
    Finding finding = requireFinding(findingId, tenantId);
    FindingTriage triage = getOrCreateTriage(finding, tenantId);
    FindingTriageStatus fromStatus = triage.getStatus();

    validateTransition(fromStatus, targetStatus);
    requireAdminIfRevert(targetStatus, currentUser);

    applyTransition(
        triage, finding, fromStatus, targetStatus, justification, currentUser, tenantId);
    return FindingTriageOutput.from(triage);
  }

  @Transactional
  public List<FindingTriageBulkItemOutput> triageBulk(
      @NotNull final List<String> findingIds,
      @NotNull final FindingTriageStatus targetStatus,
      @NotBlank final String justification) {
    String tenantId = TenantContext.getCurrentTenant();
    User currentUser = userService.currentUser();
    // Revert-to-UNTRIAGED is gated on the caller (same user for the whole batch), not on a
    // per-finding basis, so it is checked once up-front rather than collected as per-item errors.
    requireAdminIfRevert(targetStatus, currentUser);

    List<FindingTriageBulkItemOutput> results = new ArrayList<>();
    for (String findingId : findingIds) {
      try {
        Finding finding = requireFinding(findingId, tenantId);
        FindingTriage triage = getOrCreateTriage(finding, tenantId);
        FindingTriageStatus fromStatus = triage.getStatus();
        validateTransition(fromStatus, targetStatus);
        applyTransition(
            triage, finding, fromStatus, targetStatus, justification, currentUser, tenantId);
        results.add(
            FindingTriageBulkItemOutput.builder()
                .findingId(findingId)
                .success(true)
                .status(targetStatus)
                .build());
      } catch (Exception e) {
        results.add(
            FindingTriageBulkItemOutput.builder()
                .findingId(findingId)
                .success(false)
                .error(e.getMessage())
                .build());
      }
    }
    return results;
  }

  @Transactional(readOnly = true)
  public List<FindingTriageHistoryOutput> history(@NotBlank final String findingId) {
    String tenantId = TenantContext.getCurrentTenant();
    requireFinding(findingId, tenantId);
    return findingTriageHistoryRepository.findByFinding_IdOrderByCreationDateAsc(findingId).stream()
        .map(FindingTriageHistoryOutput::from)
        .toList();
  }

  // -- internals --

  private void applyTransition(
      FindingTriage triage,
      Finding finding,
      FindingTriageStatus fromStatus,
      FindingTriageStatus targetStatus,
      String justification,
      User actor,
      String tenantId) {
    triage.setStatus(targetStatus);
    triage.setUpdateDate(Instant.now());
    findingTriageRepository.save(triage);

    // Triage lives in its own table (finding_triage), so Hibernate's @UpdateTimestamp on
    // Finding#updateDate never fires from the save above - explicitly touch the parent Finding so
    // "finding_updated_at" (sortable/filterable in the findings list) reflects triage changes too.
    finding.setUpdateDate(Instant.now());
    findingRepository.save(finding);

    FindingTriageHistory history = new FindingTriageHistory();
    history.setFinding(finding);
    history.setFromStatus(fromStatus);
    history.setToStatus(targetStatus);
    history.setJustification(justification);
    history.setActor(actor);
    history.setTenant(tenantReference(tenantId));
    findingTriageHistoryRepository.save(history);
  }

  private FindingTriage getOrCreateTriage(Finding finding, String tenantId) {
    return findingTriageRepository
        .findByFinding_Id(finding.getId())
        .orElseGet(
            () -> {
              FindingTriage triage = new FindingTriage();
              triage.setFinding(finding);
              triage.setStatus(FindingTriageStatus.UNTRIAGED);
              triage.setTenant(tenantReference(tenantId));
              return findingTriageRepository.save(triage);
            });
  }

  private Finding requireFinding(String findingId, String tenantId) {
    return findingRepository
        .findByIdAndTenantId(findingId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Finding not found with id: " + findingId));
  }

  private Tenant tenantReference(String tenantId) {
    return tenantRepository.getReferenceById(tenantId);
  }

  private void validateTransition(
      FindingTriageStatus fromStatus, FindingTriageStatus targetStatus) {
    if (fromStatus == targetStatus) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Finding is already in " + targetStatus + " status");
    }
    if (targetStatus == FindingTriageStatus.UNTRIAGED) {
      // Revert is allowed from any non-UNTRIAGED status (admin-only, checked by the caller).
      return;
    }
    Set<FindingTriageStatus> allowedTargets = ALLOWED_TRANSITIONS.get(fromStatus);
    if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid triage transition from " + fromStatus + " to " + targetStatus);
    }
  }

  private void requireAdminIfRevert(FindingTriageStatus targetStatus, User currentUser) {
    if (targetStatus == FindingTriageStatus.UNTRIAGED && !currentUser.isAdmin()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only Admins can revert a finding's triage status to UNTRIAGED");
    }
  }
}
