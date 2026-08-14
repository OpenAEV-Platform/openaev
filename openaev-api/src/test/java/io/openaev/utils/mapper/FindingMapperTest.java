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

  @Nested
  @DisplayName("finding_asset_groups in AggregatedFindingOutput")
  class AssetGroupsInAggregatedOutput {

    @Autowired private io.openaev.utils.fixtures.composers.AssetGroupComposer assetGroupComposer;
    @Autowired private io.openaev.utils.fixtures.composers.EndpointComposer endpointComposer;
    @Autowired private io.openaev.database.repository.EndpointRepository endpointRepository;

    @BeforeEach
    void setUp() {
      assetGroupComposer.reset();
      endpointComposer.reset();
    }

    @Test
    @DisplayName(
        "should populate finding_asset_groups from the related assets' asset groups (regression:"
            + " previously always empty)")
    void given_relatedAssetsWithAssetGroups_should_populateAssetGroups() {
      // Arrange
      Inject inject = getDefaultInject();
      injectComposer.forInject(inject).persist();
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setInject(inject);
      findingComposer.forFinding(finding).persist();

      io.openaev.database.model.Endpoint endpoint =
          io.openaev.utils.fixtures.EndpointFixture.createEndpoint();
      io.openaev.database.model.AssetGroup assetGroup =
          io.openaev.utils.fixtures.AssetGroupFixture.createDefaultAssetGroup("Prod servers");
      assetGroupComposer
          .forAssetGroup(assetGroup)
          .withAsset(endpointComposer.forEndpoint(endpoint))
          .persist();
      entityManager.flush();
      entityManager.clear();
      // Re-fetch: the in-memory `endpoint` reference is now detached after clear() and would
      // still show an empty assetGroups collection (populated only from the inverse/mappedBy
      // side, never refreshed on the same Java instance) - a fresh managed instance is required
      // to see the join written by the AssetGroup side.
      io.openaev.database.model.Endpoint managedEndpoint =
          endpointRepository.findById(endpoint.getId()).orElseThrow();

      // Act
      AggregatedFindingOutput output =
          findingMapper.toAggregatedFindingOutput(finding, List.of(managedEndpoint));

      // Assert
      assertEquals(1, output.getAssetGroups().size());
      assertEquals(assetGroup.getId(), output.getAssetGroups().iterator().next().getId());
    }

    @Test
    @DisplayName("should leave finding_asset_groups empty when related assets have no group")
    void given_relatedAssetsWithoutAssetGroups_should_returnEmptySet() {
      // Arrange
      Inject inject = getDefaultInject();
      injectComposer.forInject(inject).persist();
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setInject(inject);
      findingComposer.forFinding(finding).persist();

      io.openaev.database.model.Endpoint endpoint =
          io.openaev.utils.fixtures.EndpointFixture.createEndpoint();
      endpointComposer.forEndpoint(endpoint).persist();

      // Act
      AggregatedFindingOutput output =
          findingMapper.toAggregatedFindingOutput(finding, List.of(endpoint));

      // Assert
      assertEquals(0, output.getAssetGroups().size());
    }
  }
}
