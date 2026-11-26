package io.openaev.service.finding;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingStatus;
import io.openaev.database.repository.FindingRepository;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Settles the findings a finished inject was re-testing. Called by the inject finalization, in the
 * transaction that sets the final status, so the finding and the inject move together.
 *
 * <p>A re-test that detected the finding again already set it {@link FindingStatus#OPEN} with the
 * re-test inject as its run, through the recording path ({@link
 * FindingRetestService#markDetected}). A successful re-test that did not detect it sets {@link
 * FindingStatus#NOT_REPRODUCED} with the re-test inject as its run. A re-test that ended in error
 * proves nothing: the status is kept. In every case the pending re-test is cleared and the re-test
 * date stamped. Each status change is an update of the finding row, so the entity listener emits
 * the finding update event.
 */
@Component
@RequiredArgsConstructor
public class FindingRetestSettlement {

  private final FindingRepository findingRepository;

  /**
   * @param injectId the inject that reached its final status
   * @param tenantId its tenant
   * @param finalStatus the status it reached
   */
  public void settleRetestsOf(
      @NotNull final String injectId,
      @NotNull final String tenantId,
      final ExecutionStatus finalStatus) {
    List<Finding> pending =
        findingRepository.findAllByRetestInjectIdAndTenantId(injectId, tenantId);
    if (pending.isEmpty()) {
      return;
    }
    boolean conclusive = finalStatus != null && finalStatus.isSuccess();
    Instant now = Instant.now();
    pending.forEach(finding -> settle(finding, injectId, conclusive, now));
    findingRepository.saveAll(pending);
  }

  /** The status one finished re-test leaves on one finding. */
  static void settle(Finding finding, String retestInjectId, boolean conclusive, Instant now) {
    finding.setRetestInjectId(null);
    finding.setLastTested(now);
    boolean reproduced = retestInjectId.equals(finding.getStatusInjectId());
    if (reproduced || !conclusive) {
      return;
    }
    finding.setStatus(FindingStatus.NOT_REPRODUCED);
    finding.setStatusInjectId(retestInjectId);
  }
}
