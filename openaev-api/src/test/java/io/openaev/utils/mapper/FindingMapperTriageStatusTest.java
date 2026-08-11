package io.openaev.utils.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FindingMapper}, focused on the {@code finding_triage_status} field
 * introduced on {@link AggregatedFindingOutput}/{@link RelatedFindingOutput}. The mapper itself has
 * no dependency on {@code FindingTriageRepository} (bulk fetch is the caller's responsibility), so
 * these tests only assert the merge logic against a pre-built map.
 *
 * <p>Kept in a separate file from {@link FindingMapperTest} (which covers {@code finding_source}
 * with real integration-test persistence via composers): the two suites use incompatible test
 * styles (pure Mockito unit test here vs. a Spring {@code IntegrationTest} there) and can't share a
 * single test class.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FindingMapper — finding_triage_status")
class FindingMapperTriageStatusTest {

  @Mock private FindingRepository findingRepository;
  @Mock private EndpointMapper endpointMapper;
  @Mock private AssetGroupMapper assetGroupMapper;
  @Mock private ExerciseMapper exerciseMapper;
  @Mock private ScenarioMapper scenarioMapper;
  @Mock private InjectMapper injectMapper;
  @Mock private InjectorMapper injectorMapper;

  private FindingMapper findingMapper;

  @BeforeEach
  void setUp() {
    findingMapper =
        new FindingMapper(
            findingRepository,
            endpointMapper,
            assetGroupMapper,
            exerciseMapper,
            scenarioMapper,
            injectMapper,
            injectorMapper);
  }

  private Finding buildFinding(String id) {
    Finding finding = new Finding();
    finding.setId(id);
    finding.setInject(new Inject());
    return finding;
  }

  @Nested
  @DisplayName("toAggregatedFindingOutput")
  class ToAggregatedFindingOutputTests {

    @Test
    @DisplayName("should default to UNTRIAGED when triage map has no entry for the finding")
    void shouldDefaultToUntriagedWhenMapEmpty() {
      // Arrange
      Finding finding = buildFinding("finding-1");

      // Act
      AggregatedFindingOutput output =
          findingMapper.toAggregatedFindingOutput(finding, List.of(), Map.of());

      // Assert
      assertEquals(FindingTriageStatus.UNTRIAGED, output.getFindingTriageStatus());
    }

    @Test
    @DisplayName("should return the status found in the triage map for the finding id")
    void shouldReturnStatusFromMap() {
      // Arrange
      Finding finding = buildFinding("finding-2");
      Map<String, FindingTriageStatus> triageStatusByFindingId =
          Map.of("finding-2", FindingTriageStatus.CONFIRMED);

      // Act
      AggregatedFindingOutput output =
          findingMapper.toAggregatedFindingOutput(finding, List.of(), triageStatusByFindingId);

      // Assert
      assertEquals(FindingTriageStatus.CONFIRMED, output.getFindingTriageStatus());
    }

    @Test
    @DisplayName("legacy single-finding overload should default to UNTRIAGED")
    void legacyOverloadShouldDefaultToUntriaged() {
      // Arrange
      Finding finding = buildFinding("finding-3");

      // Act
      AggregatedFindingOutput output = findingMapper.toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertEquals(FindingTriageStatus.UNTRIAGED, output.getFindingTriageStatus());
    }

    @Test
    @DisplayName("should never call FindingRepository (mapper stays pure, no N+1 risk)")
    void shouldNeverTouchFindingRepository() {
      // Arrange
      Finding finding = buildFinding("finding-4");

      // Act
      findingMapper.toAggregatedFindingOutput(finding, List.of(), Map.of());

      // Assert
      verifyNoInteractions(findingRepository);
    }
  }

  @Nested
  @DisplayName("toRelatedFindingOutput")
  class ToRelatedFindingOutputTests {

    @Test
    @DisplayName("should default to UNTRIAGED when triage map has no entry for the finding")
    void shouldDefaultToUntriagedWhenMapEmpty() {
      // Arrange
      Finding finding = buildFinding("finding-5");

      // Act
      RelatedFindingOutput output = findingMapper.toRelatedFindingOutput(finding, Map.of());

      // Assert
      assertEquals(FindingTriageStatus.UNTRIAGED, output.getFindingTriageStatus());
    }

    @Test
    @DisplayName("should return the status found in the triage map for the finding id")
    void shouldReturnStatusFromMap() {
      // Arrange
      Finding finding = buildFinding("finding-6");
      Map<String, FindingTriageStatus> triageStatusByFindingId =
          Map.of("finding-6", FindingTriageStatus.FALSE_POSITIVE);

      // Act
      RelatedFindingOutput output =
          findingMapper.toRelatedFindingOutput(finding, triageStatusByFindingId);

      // Assert
      assertEquals(FindingTriageStatus.FALSE_POSITIVE, output.getFindingTriageStatus());
    }

    @Test
    @DisplayName("legacy single-finding overload should default to UNTRIAGED")
    void legacyOverloadShouldDefaultToUntriaged() {
      // Arrange
      Finding finding = buildFinding("finding-7");

      // Act
      RelatedFindingOutput output = findingMapper.toRelatedFindingOutput(finding);

      // Assert
      assertEquals(FindingTriageStatus.UNTRIAGED, output.getFindingTriageStatus());
    }

    @Test
    @DisplayName("should never call FindingRepository (mapper stays pure, no N+1 risk)")
    void shouldNeverTouchFindingRepository() {
      // Arrange
      Finding finding = buildFinding("finding-8");

      // Act
      findingMapper.toRelatedFindingOutput(finding, Map.of());

      // Assert
      verifyNoInteractions(findingRepository);
    }
  }
}
