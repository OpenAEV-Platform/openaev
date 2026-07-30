package io.openaev.rest.inject_expectation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectExpectationIndexing;
import io.openaev.database.repository.*;
import io.openaev.expectation.ExpectationType;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.fixtures.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Covers the global-score aggregation queries used by inject lists, simulation cards and scenario
 * statistics (issue #7027).
 *
 * <p>The global score must be computed from primary (target-level) expectations only: the per-asset
 * children of a targeted asset group are already rolled up into the group parent row according to
 * the configured validation mode ("all assets" vs "at least one asset"), so counting them again
 * dilutes a failed group into a PARTIAL global score.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectExpectationGlobalScoreTest extends IntegrationTest {

  private static final String INJECTION_NAME = "Global score aggregation inject";
  private static final Double EXPECTED_SCORE = 100.0;
  private static final Long EXPIRATION_TIME = 21600L;

  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private ResultUtils resultUtils;

  private InjectorContract savedInjectorContract;
  private Asset asset1;
  private Asset asset2;
  private Asset asset3;

  @BeforeAll
  void beforeAll() throws JsonProcessingException {
    InjectorContract injectorContract =
        InjectorContractFixture.createInjectorContract(Map.of("en", INJECTION_NAME));
    Injector savedInjector = injectorFixture.getWellKnownOaevImplantInjector();
    injectorContract.addInjector(savedInjector);
    savedInjectorContract = injectorContractRepository.save(injectorContract);
    savedInjector.linkContract(savedInjectorContract);
    injectorRepository.save(savedInjector);

    asset1 = assetRepository.save(AssetFixture.createDefaultAsset("global-score-asset-1"));
    asset2 = assetRepository.save(AssetFixture.createDefaultAsset("global-score-asset-2"));
    asset3 = assetRepository.save(AssetFixture.createDefaultAsset("global-score-asset-3"));
  }

  @AfterEach
  void afterEach() {
    injectExpectationRepository.deleteAll();
    injectRepository.deleteAll();
    assetGroupRepository.deleteAll();
  }

  @AfterAll
  void afterAll() {
    assetRepository.deleteAll();
  }

  private Inject saveInjectWithAssetGroup(AssetGroup assetGroup) {
    Inject inject =
        InjectFixture.createTechnicalInjectWithAssetGroup(
            savedInjectorContract, INJECTION_NAME, assetGroup);
    inject.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return injectRepository.save(inject);
  }

  private VulnerabilityInjectExpectation saveVulnerabilityExpectation(
      Inject inject, Asset asset, AssetGroup assetGroup, Double score, boolean expectationGroup) {
    VulnerabilityInjectExpectation expectation = new VulnerabilityInjectExpectation();
    expectation.setInject(inject);
    expectation.setAsset(asset);
    expectation.setAssetGroup(assetGroup);
    expectation.setName("Not vulnerable");
    expectation.setScore(score);
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME);
    expectation.setExpectationGroup(expectationGroup);
    return injectExpectationRepository.save(expectation);
  }

  @Test
  @DisplayName(
      "Global score should be FAILED when the asset group expectation fails in all-assets"
          + " validation mode, even if most per-asset children succeeded")
  void globalScoreShouldBeFailedWhenAssetGroupAllModeFails() {
    AssetGroup assetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "all endpoints", List.of(asset1, asset2, asset3)));
    Inject inject = saveInjectWithAssetGroup(assetGroup);

    // Group parent row: "all assets must validate" failed because one asset is vulnerable
    saveVulnerabilityExpectation(inject, null, assetGroup, 0.0, false);
    // Per-asset children: two not vulnerable, one vulnerable
    saveVulnerabilityExpectation(inject, asset1, assetGroup, 100.0, false);
    saveVulnerabilityExpectation(inject, asset2, assetGroup, 100.0, false);
    saveVulnerabilityExpectation(inject, asset3, assetGroup, 0.0, false);

    List<RawInjectExpectationIndexing> raws =
        injectExpectationRepository.rawForComputeGlobalByInjectIds(Set.of(inject.getId()));

    // Only the group parent row is aggregated, not its per-asset children
    assertThat(raws).hasSize(1);
    assertThat(raws.get(0).getAsset_id()).isNull();
    assertThat(raws.get(0).getAsset_group_id()).isEqualTo(assetGroup.getId());

    List<ExpectationResultsByType> results =
        resultUtils.computeGlobalExpectationResults(Set.of(inject.getId()));

    assertThat(results)
        .filteredOn(result -> result.type() == ExpectationType.VULNERABILITY)
        .singleElement()
        .extracting(ExpectationResultsByType::avgResult)
        .isEqualTo(BaseInjectExpectation.EXPECTATION_STATUS.FAILED);
  }

  @Test
  @DisplayName(
      "Global score should be SUCCESS when the asset group expectation succeeds in at-least-one"
          + " validation mode, even if some per-asset children failed")
  void globalScoreShouldBeSuccessWhenAssetGroupAtLeastOneModeSucceeds() {
    AssetGroup assetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "all endpoints", List.of(asset1, asset2, asset3)));
    Inject inject = saveInjectWithAssetGroup(assetGroup);

    // Group parent row: "at least one asset must validate" succeeded
    saveVulnerabilityExpectation(inject, null, assetGroup, 100.0, true);
    // Per-asset children: one not vulnerable, two vulnerable
    saveVulnerabilityExpectation(inject, asset1, assetGroup, 100.0, true);
    saveVulnerabilityExpectation(inject, asset2, assetGroup, 0.0, true);
    saveVulnerabilityExpectation(inject, asset3, assetGroup, 0.0, true);

    List<ExpectationResultsByType> results =
        resultUtils.computeGlobalExpectationResults(Set.of(inject.getId()));

    assertThat(results)
        .filteredOn(result -> result.type() == ExpectationType.VULNERABILITY)
        .singleElement()
        .extracting(ExpectationResultsByType::avgResult)
        .isEqualTo(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
  }

  @Test
  @DisplayName(
      "Directly-targeted asset expectations should still count in the global score, including"
          + " assets that also belong to a targeted asset group")
  void globalScoreShouldKeepDirectlyTargetedAssets() {
    // asset1 is both a direct inject target and a member of the targeted group
    AssetGroup assetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets("all endpoints", List.of(asset1, asset2)));
    Inject inject =
        InjectFixture.createTechnicalInjectWithAssetGroup(
            savedInjectorContract, INJECTION_NAME, assetGroup);
    inject.setAssets(List.of(asset1, asset3));
    inject.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    inject = injectRepository.save(inject);

    // Group parent row succeeded
    saveVulnerabilityExpectation(inject, null, assetGroup, 100.0, false);
    // asset1: direct target AND group member, its row carries the asset group reference
    saveVulnerabilityExpectation(inject, asset1, assetGroup, 100.0, false);
    // asset2: group member only, rolled up into the parent
    saveVulnerabilityExpectation(inject, asset2, assetGroup, 100.0, false);
    // asset3: direct target only, failed
    saveVulnerabilityExpectation(inject, asset3, null, 0.0, false);

    List<RawInjectExpectationIndexing> raws =
        injectExpectationRepository.rawForComputeGlobalByInjectIds(Set.of(inject.getId()));

    // Parent + asset1 (direct target) + asset3 (direct target); asset2 child is excluded
    assertThat(raws).hasSize(3);
    assertThat(raws)
        .noneMatch(
            raw -> asset2.getId().equals(raw.getAsset_id()) && raw.getAsset_group_id() != null);

    List<ExpectationResultsByType> results =
        resultUtils.computeGlobalExpectationResults(Set.of(inject.getId()));

    // Two successes (parent + asset1) and one failure (asset3) -> PARTIAL
    assertThat(results)
        .filteredOn(result -> result.type() == ExpectationType.VULNERABILITY)
        .singleElement()
        .extracting(ExpectationResultsByType::avgResult)
        .isEqualTo(BaseInjectExpectation.EXPECTATION_STATUS.PARTIAL);
  }

  @Test
  @DisplayName(
      "Entity-based global score query should apply the same primary-expectation filtering")
  void entityGlobalScoreQueryShouldExcludeAssetGroupChildren() {
    AssetGroup assetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "all endpoints", List.of(asset1, asset2, asset3)));
    Inject inject = saveInjectWithAssetGroup(assetGroup);

    saveVulnerabilityExpectation(inject, null, assetGroup, 0.0, false);
    saveVulnerabilityExpectation(inject, asset1, assetGroup, 100.0, false);
    saveVulnerabilityExpectation(inject, asset2, assetGroup, 100.0, false);
    saveVulnerabilityExpectation(inject, asset3, assetGroup, 0.0, false);

    List<BaseInjectExpectation> expectations =
        injectExpectationRepository.findAllForGlobalScoreByInjects(Set.of(inject.getId()));

    assertThat(expectations).hasSize(1);
    assertThat(expectations.get(0)).isInstanceOf(TechnicalInjectExpectation.class);
    TechnicalInjectExpectation parent = (TechnicalInjectExpectation) expectations.get(0);
    assertThat(parent.getAsset()).isNull();
    assertThat(parent.getAssetGroup()).isNotNull();
  }
}
