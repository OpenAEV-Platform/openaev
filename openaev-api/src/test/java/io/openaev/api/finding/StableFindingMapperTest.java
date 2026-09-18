package io.openaev.api.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.StableFinding;
import io.openaev.database.model.StableFindingCategory;
import io.openaev.database.model.StableFindingLifecycle;
import io.openaev.service.finding.SeverityNormalizationService;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.InjectorMapper;
import io.openaev.utils.mapper.ScenarioMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Stable finding mapper")
class StableFindingMapperTest {

  private final StableFindingMapper mapper =
      new StableFindingMapper(
          mock(EndpointMapper.class),
          mock(AssetGroupMapper.class),
          mock(ExerciseMapper.class),
          mock(ScenarioMapper.class),
          mock(InjectMapper.class),
          mock(InjectorMapper.class),
          new SeverityNormalizationService());

  @Nested
  @DisplayName("Provider aggregation")
  class ProviderAggregation {

    @Test
    @DisplayName("Returns the provider when all nonblank observations agree")
    void given_agreeingProviders_should_returnNormalizedProvider() {
      // Arrange
      FindingOccurrence first = occurrence(" AWS ");
      FindingOccurrence second = occurrence("aws");
      FindingOccurrence blank = occurrence(" ");

      // Act
      String provider = StableFindingMapper.aggregateProvider(List.of(first, second, blank));

      // Assert
      assertThat(provider).isEqualTo("aws");
    }

    @Test
    @DisplayName("Returns null when observations have conflicting providers")
    void given_conflictingProviders_should_returnNull() {
      // Arrange
      FindingOccurrence aws = occurrence("aws");
      FindingOccurrence azure = occurrence("azure");

      // Act
      String provider = StableFindingMapper.aggregateProvider(List.of(aws, azure));

      // Assert
      assertThat(provider).isNull();
    }

    @Test
    @DisplayName("Serializes an unknown aggregate provider explicitly as null")
    void given_unknownProvider_should_serializeExplicitNull() throws Exception {
      // Arrange
      StableFindingOutput output = StableFindingOutput.builder().cloudProvider(null).build();

      // Act
      String json = new ObjectMapper().writeValueAsString(output);

      // Assert
      assertThat(json).contains("\"finding_cloud_provider\":null");
    }
  }

  @Nested
  @DisplayName("Stable output")
  class StableOutput {

    @Test
    @DisplayName("Uses stable dates and latest occurrence evidence")
    void given_orderedOccurrences_should_mapStableAndLatestFields() {
      // Arrange
      StableFinding finding = stableFinding();
      FindingOccurrence latest = occurrence("aws");
      latest.setStableFinding(finding);
      latest.setObservedAt(Instant.parse("2026-09-17T09:00:00Z"));
      latest.setLocationType(io.openaev.database.model.FindingLocationType.RESOURCE);
      latest.setLocationKey("bucket-a");
      latest.setLocation("Bucket A");
      latest.setTitle("Latest title");
      latest.setOutcome("FAIL");
      latest.setEvidenceDetail("latest evidence");
      latest.setObservedSeverity("High");
      latest.setObservedSeverityId(4);
      FindingOccurrence older = occurrence("aws");
      older.setStableFinding(finding);
      older.setObservedAt(Instant.parse("2026-09-16T09:00:00Z"));
      older.setTitle("Old title");
      older.setEvidenceDetail("old evidence");

      // Act
      StableFindingOutput output = mapper.toOutput(finding, List.of(latest, older));

      // Assert
      assertThat(output.createdAt()).isEqualTo(finding.getFirstSeen());
      assertThat(output.updatedAt()).isEqualTo(finding.getLastSeen());
      assertThat(output.title()).isEqualTo("Latest title");
      assertThat(output.evidenceDetail()).isEqualTo("latest evidence");
      assertThat(output.outcome()).isEqualTo("FAIL");
      assertThat(output.severity()).isEqualTo("HIGH");
      assertThat(output.severityId()).isEqualTo(4);
      assertThat(output.occurrences()).isEqualTo(2);
      assertThat(output.cloudProvider()).isEqualTo("aws");
      assertThat(output.locationKey()).isEqualTo("bucket-a");
    }

    @Test
    @DisplayName("Never exposes a PASS outcome")
    void given_passOutcome_should_omitOutcome() {
      // Arrange
      StableFinding finding = stableFinding();
      FindingOccurrence occurrence = occurrence(null);
      occurrence.setStableFinding(finding);
      occurrence.setObservedAt(Instant.parse("2026-09-17T09:00:00Z"));
      occurrence.setOutcome("PASS");

      // Act
      StableFindingOutput output = mapper.toOutput(finding, List.of(occurrence));
      FindingOccurrenceOutput occurrenceOutput = mapper.toOccurrenceOutput(occurrence);

      // Assert
      assertThat(output.outcome()).isNull();
      assertThat(occurrenceOutput.outcome()).isNull();
    }
  }

  private static StableFinding stableFinding() {
    StableFinding finding = new StableFinding();
    finding.setId("stable-id");
    finding.setContractOutputKey("result");
    finding.setType(ContractOutputType.Text);
    finding.setValue("finding-value");
    finding.setCategory(StableFindingCategory.INFORMATIVE);
    finding.setLifecycle(StableFindingLifecycle.ACTIVE);
    finding.setFirstSeen(Instant.parse("2026-09-15T09:00:00Z"));
    finding.setLastSeen(Instant.parse("2026-09-17T09:00:00Z"));
    return finding;
  }

  private static FindingOccurrence occurrence(String provider) {
    FindingOccurrence occurrence = new FindingOccurrence();
    occurrence.setResourceProvider(provider);
    return occurrence;
  }
}
