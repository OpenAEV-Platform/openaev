package io.openaev.utils.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.api.injector.dto.InjectorSimple;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.FindingOccurrenceFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FindingMapper tests")
class FindingMapperTest {

  @Mock private EndpointMapper endpointMapper;
  @Mock private AssetGroupMapper assetGroupMapper;
  @Mock private ExerciseMapper exerciseMapper;
  @Mock private ScenarioMapper scenarioMapper;
  @Mock private InjectMapper injectMapper;
  @Mock private AssetGroupService assetGroupService;

  private FindingMapper buildMapper() {
    return new FindingMapper(
        endpointMapper,
        assetGroupMapper,
        exerciseMapper,
        scenarioMapper,
        injectMapper,
        assetGroupService);
  }

  private static Inject injectDetectedBy(Injector injector) {
    Inject inject = new Inject();
    inject.setInjector(injector);
    return inject;
  }

  private static Finding findingOccurringIn(Inject... injects) {
    Finding finding = FindingFixture.createDefaultTextFinding();
    for (Inject inject : injects) {
      finding
          .getOccurrences()
          .add(FindingOccurrenceFixture.createOccurrence(finding, inject, Instant.now(), null));
    }
    return finding;
  }

  @Nested
  @DisplayName("Sources of an aggregated finding")
  class Sources {

    @Test
    @DisplayName("Should expose the id, name and type of the injector of each occurrence")
    void given_occurrencesFromTwoInjectors_should_exposeBothAsSources() {
      // Arrange
      Injector nuclei = InjectorFixture.createInjector("nuclei-id", "Nuclei", "openaev_nuclei");
      Injector email = InjectorFixture.createInjector("email-id", "Email", "openaev_email");
      Finding finding = findingOccurringIn(injectDetectedBy(nuclei), injectDetectedBy(email));

      // Act
      AggregatedFindingOutput output = buildMapper().toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertThat(output.getSources())
          .containsExactlyInAnyOrder(
              InjectorSimple.builder()
                  .id("nuclei-id")
                  .name("Nuclei")
                  .type("openaev_nuclei")
                  .build(),
              InjectorSimple.builder().id("email-id").name("Email").type("openaev_email").build());
    }

    @Test
    @DisplayName("Should list an injector once when several occurrences come from it")
    void given_severalOccurrencesFromTheSameInjector_should_listItOnce() {
      // Arrange
      Injector nuclei = InjectorFixture.createInjector("nuclei-id", "Nuclei", "openaev_nuclei");
      Finding finding = findingOccurringIn(injectDetectedBy(nuclei), injectDetectedBy(nuclei));

      // Act
      AggregatedFindingOutput output = buildMapper().toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertThat(output.getSources()).hasSize(1);
    }

    @Test
    @DisplayName("Should skip an occurrence whose inject has no injector")
    void given_anInjectWithoutInjector_should_skipIt() {
      // Arrange
      Injector nuclei = InjectorFixture.createInjector("nuclei-id", "Nuclei", "openaev_nuclei");
      Finding finding = findingOccurringIn(injectDetectedBy(nuclei), injectDetectedBy(null));

      // Act
      AggregatedFindingOutput output = buildMapper().toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertThat(output.getSources()).extracting(InjectorSimple::id).containsExactly("nuclei-id");
    }

    @Test
    @DisplayName("Should return an empty set, not null, when the finding has no occurrence")
    void given_aFindingWithoutOccurrence_should_returnNoSource() {
      // Arrange
      Finding finding = findingOccurringIn();

      // Act
      AggregatedFindingOutput output = buildMapper().toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertThat(output.getSources()).isNotNull().isEmpty();
    }
  }
}
