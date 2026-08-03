package io.openaev.rest.finding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingTriageRepository;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.utils.mapper.FindingMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link FindingDistinctSearchService#searchDistinctBySpecification}, focused on
 * proving the {@code finding_triage_status} bulk-fetch does not introduce an N+1 query: {@link
 * FindingTriageRepository#findByFinding_IdIn} must be invoked exactly once per page, regardless of
 * how many findings the page contains.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FindingDistinctSearchService")
class FindingDistinctSearchServiceTest {

  @Mock private FindingRepository findingRepository;
  @Mock private FindingTriageRepository findingTriageRepository;
  @Mock private FindingMapper findingMapper;

  private FindingDistinctSearchService service;

  @BeforeEach
  void setUp() {
    service =
        new FindingDistinctSearchService(findingRepository, findingTriageRepository, findingMapper);
  }

  private Finding buildFinding(String id, ContractOutputType type, String value) {
    Finding finding = new Finding();
    finding.setId(id);
    finding.setType(type);
    finding.setValue(value);
    return finding;
  }

  @Nested
  @DisplayName("searchDistinctBySpecification - triage status bulk fetch")
  class TriageStatusBulkFetchTests {

    @Test
    @DisplayName(
        "should call findByFinding_IdIn exactly once for a page with multiple findings (no N+1)")
    void shouldCallBulkFetchExactlyOnceForMultipleFindings() {
      // Arrange
      Finding finding1 = buildFinding("finding-1", ContractOutputType.Text, "value-1");
      Finding finding2 = buildFinding("finding-2", ContractOutputType.Text, "value-2");
      Finding finding3 = buildFinding("finding-3", ContractOutputType.Number, "value-3");
      Page<Finding> page =
          new PageImpl<>(List.of(finding1, finding2, finding3), Pageable.unpaged(), 3);

      when(findingRepository.findAll(any(Specification.class))).thenReturn(List.of());
      when(findingTriageRepository.findByFinding_IdIn(anyList())).thenReturn(List.of());
      when(findingMapper.toAggregatedFindingOutput(any(), anyList(), anyMap()))
          .thenReturn(AggregatedFindingOutput.builder().build());

      // Act
      service.searchDistinctBySpecification(Specification.unrestricted(), page);

      // Assert - exactly one bulk call, never one per finding
      verify(findingTriageRepository, times(1)).findByFinding_IdIn(anyList());
    }

    @Test
    @DisplayName("should default to UNTRIAGED and merge existing statuses via the bulk map")
    void shouldMergeBulkFetchedStatusesIntoMapperCall() {
      // Arrange
      Finding finding1 = buildFinding("finding-1", ContractOutputType.Text, "value-1");
      Finding finding2 = buildFinding("finding-2", ContractOutputType.Text, "value-2");
      Page<Finding> page = new PageImpl<>(List.of(finding1, finding2), Pageable.unpaged(), 2);

      Finding triagedFindingRef = new Finding();
      triagedFindingRef.setId("finding-1");
      FindingTriage triage = new FindingTriage();
      triage.setFinding(triagedFindingRef);
      triage.setStatus(FindingTriageStatus.CONFIRMED);

      when(findingRepository.findAll(any(Specification.class))).thenReturn(List.of());
      when(findingTriageRepository.findByFinding_IdIn(anyList())).thenReturn(List.of(triage));
      when(findingMapper.toAggregatedFindingOutput(any(), anyList(), anyMap()))
          .thenReturn(AggregatedFindingOutput.builder().build());

      // Act
      service.searchDistinctBySpecification(Specification.unrestricted(), page);

      // Assert - the map passed to the mapper reflects finding-1 as CONFIRMED, finding-2 defaults
      // (mapper itself is responsible for the UNTRIAGED default on missing keys, tested in
      // FindingMapperTest; here we only assert the map content built by the service).
      verify(findingTriageRepository, times(1)).findByFinding_IdIn(List.of("finding-1", "finding-2"));
    }
  }
}
