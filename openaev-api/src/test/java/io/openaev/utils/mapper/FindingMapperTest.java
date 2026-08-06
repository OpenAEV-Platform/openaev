package io.openaev.utils.mapper;

import static io.openaev.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("FindingMapper — finding_source")
class FindingMapperTest extends IntegrationTest {

  @Autowired private FindingMapper findingMapper;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorRepository injectorRepository;

  @BeforeEach
  void setUp() {
    findingComposer.reset();
    injectComposer.reset();
  }

  @Nested
  @DisplayName("when the inject has a real injector")
  class WithInjector {

    @Test
    @DisplayName(
        "should populate finding_source with the injector id/name/type in AggregatedFindingOutput")
    void given_findingWithInjector_should_populateSourceInAggregatedOutput() {
      // Arrange
      Injector injector = injectorRepository.save(InjectorFixture.createDefaultInjector("Nuclei"));
      Inject inject = getDefaultInject();
      inject.setInjector(injector);
      injectComposer.forInject(inject).persist();
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setInject(inject);
      findingComposer.forFinding(finding).persist();

      // Act
      AggregatedFindingOutput output = findingMapper.toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertEquals(injector.getId(), output.getSource().getId());
      assertEquals(injector.getName(), output.getSource().getName());
      assertEquals(injector.getType(), output.getSource().getType());
    }

    @Test
    @DisplayName(
        "should populate finding_source with the injector id/name/type in RelatedFindingOutput")
    void given_findingWithInjector_should_populateSourceInRelatedOutput() {
      // Arrange
      Injector injector = injectorRepository.save(InjectorFixture.createDefaultInjector("NetExec"));
      Inject inject = getDefaultInject();
      inject.setInjector(injector);
      injectComposer.forInject(inject).persist();
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setInject(inject);
      findingComposer.forFinding(finding).persist();

      // Act
      RelatedFindingOutput output = findingMapper.toRelatedFindingOutput(finding);

      // Assert
      assertEquals(injector.getId(), output.getSource().getId());
      assertEquals(injector.getName(), output.getSource().getName());
      assertEquals(injector.getType(), output.getSource().getType());
    }
  }

  @Nested
  @DisplayName("when the inject has no injector (manual finding)")
  class WithoutInjector {

    @Test
    @DisplayName("should leave finding_source null in AggregatedFindingOutput, no NPE")
    void given_findingWithoutInjector_should_leaveSourceNullInAggregatedOutput() {
      // Arrange
      Inject inject = getDefaultInject();
      injectComposer.forInject(inject).persist();
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setInject(inject);
      findingComposer.forFinding(finding).persist();

      // Act
      AggregatedFindingOutput output = findingMapper.toAggregatedFindingOutput(finding, List.of());

      // Assert
      assertNull(output.getSource());
    }

    @Test
    @DisplayName("should leave finding_source null in RelatedFindingOutput, no NPE")
    void given_findingWithoutInjector_should_leaveSourceNullInRelatedOutput() {
      // Arrange
      Inject inject = getDefaultInject();
      injectComposer.forInject(inject).persist();
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setInject(inject);
      findingComposer.forFinding(finding).persist();

      // Act
      RelatedFindingOutput output = findingMapper.toRelatedFindingOutput(finding);

      // Assert
      assertNull(output.getSource());
    }
  }
}
