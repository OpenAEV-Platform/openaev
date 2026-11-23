package io.openaev.service.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingStatus;
import io.openaev.database.repository.FindingRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindingRetestSettlementTest {

  private static final String TENANT = "tenant-1";
  private static final String ORIGINAL_INJECT = "inject-original";
  private static final String RETEST_INJECT = "inject-retest";

  private final FindingRepository findingRepository = mock(FindingRepository.class);
  private final FindingRetestSettlement settlement = new FindingRetestSettlement(findingRepository);

  private static Finding pendingRetest() {
    Finding finding = new Finding();
    finding.setId("finding-1");
    FindingRetestService.markDetected(finding, ORIGINAL_INJECT);
    finding.setRetestInjectId(RETEST_INJECT);
    return finding;
  }

  @Test
  @DisplayName("a clean re-test sets not reproduced, with the re-test inject as its run")
  void cleanRetestSetsNotReproduced() {
    Finding finding = pendingRetest();
    when(findingRepository.findAllByRetestInjectIdAndTenantId(RETEST_INJECT, TENANT))
        .thenReturn(List.of(finding));

    settlement.settleRetestsOf(RETEST_INJECT, TENANT, ExecutionStatus.EXECUTED);

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.NOT_REPRODUCED);
    assertThat(finding.getStatusInjectId()).isEqualTo(RETEST_INJECT);
    assertThat(finding.getRetestInjectId()).isNull();
    assertThat(finding.getLastTested()).isNotNull();
    verify(findingRepository).saveAll(List.of(finding));
  }

  @Test
  @DisplayName("a re-test that detected the finding again keeps it open")
  void reproducedRetestKeepsOpen() {
    Finding finding = pendingRetest();
    // the recording path ran while the re-test inject reported its detections
    FindingRetestService.markDetected(finding, RETEST_INJECT);
    when(findingRepository.findAllByRetestInjectIdAndTenantId(RETEST_INJECT, TENANT))
        .thenReturn(List.of(finding));

    settlement.settleRetestsOf(RETEST_INJECT, TENANT, ExecutionStatus.EXECUTED);

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.OPEN);
    assertThat(finding.getStatusInjectId()).isEqualTo(RETEST_INJECT);
    assertThat(finding.getRetestInjectId()).isNull();
    assertThat(finding.getLastTested()).isNotNull();
  }

  @Test
  @DisplayName("a re-test that ended in error proves nothing: the status is kept")
  void failedRetestKeepsStatus() {
    Finding finding = pendingRetest();
    when(findingRepository.findAllByRetestInjectIdAndTenantId(RETEST_INJECT, TENANT))
        .thenReturn(List.of(finding));

    settlement.settleRetestsOf(RETEST_INJECT, TENANT, ExecutionStatus.ERROR);

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.OPEN);
    assertThat(finding.getStatusInjectId()).isEqualTo(ORIGINAL_INJECT);
    assertThat(finding.getRetestInjectId()).isNull();
  }

  @Test
  @DisplayName("an inject that re-tests nothing writes nothing")
  void noPendingRetestWritesNothing() {
    when(findingRepository.findAllByRetestInjectIdAndTenantId(ORIGINAL_INJECT, TENANT))
        .thenReturn(List.of());

    settlement.settleRetestsOf(ORIGINAL_INJECT, TENANT, ExecutionStatus.EXECUTED);

    verify(findingRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("a new detection after not reproduced opens the finding again")
  void detectionReopens() {
    Finding finding = pendingRetest();
    FindingRetestSettlement.settle(finding, RETEST_INJECT, true, Instant.now());
    assertThat(finding.getStatus()).isEqualTo(FindingStatus.NOT_REPRODUCED);

    FindingRetestService.markDetected(finding, "inject-later");

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.OPEN);
    assertThat(finding.getStatusInjectId()).isEqualTo("inject-later");
  }

  @Test
  @DisplayName("a new finding starts open")
  void newFindingIsOpen() {
    assertThat(new Finding().getStatus()).isEqualTo(FindingStatus.OPEN);
  }
}
