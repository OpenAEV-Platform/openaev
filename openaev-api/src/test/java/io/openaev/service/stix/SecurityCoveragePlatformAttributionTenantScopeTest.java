package io.openaev.service.stix;

import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createCollectorResult;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.RelationshipObject;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.types.Complex;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityCoverageFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.SecurityCoverageComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platforms a bundle attributes results to are the ones resolved in the scope the bundle is
 * built in (assets is a v2 table, the security coverage job builds each bundle under its tenant's
 * {@link TxCtx}). A result whose source asset is another tenant's security platform must therefore
 * never surface in {@code coverage_platforms} nor as an identity of the bundle.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("coverage_platforms only references security platforms of the bundle's tenant")
class SecurityCoveragePlatformAttributionTenantScopeTest extends IntegrationTest {

  private static final String TECHNIQUE_EXTERNAL_ID = "T9201";

  @Autowired private SecurityCoverageService securityCoverageService;
  @Autowired private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper mapper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  @BeforeEach
  void setup() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    injectorContractComposer.reset();
    endpointComposer.reset();
    attackPatternComposer.reset();
    securityCoverageComposer.reset();
    securityPlatformComposer.reset();
  }

  private String seedSecurityPlatform(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, security_platform_type) VALUES (?, ?, 'SecurityPlatform', now(), now(),"
            + " ?, 'EDR')",
        id,
        name,
        tenantId);
    return id;
  }

  /**
   * A finished simulation of the default tenant whose only detection expectation carries a result
   * of its own platform and a result whose source asset is a platform of another tenant.
   */
  private Exercise seedSimulationWithResultsOfBothTenants(
      SecurityPlatform ownPlatform, SecurityPlatform foreignPlatform) {
    AttackPatternComposer.Composer technique =
        attackPatternComposer
            .forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId(TECHNIQUE_EXTERNAL_ID))
            .persist();
    InjectExpectationComposer.Composer detection =
        injectExpectationComposer
            .forExpectation(
                InjectExpectationFixture.createExpectationWithTypeAndStatus(
                    BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                    BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
            .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()));
    detection
        .get()
        .setResults(
            new ArrayList<>(
                List.of(
                    createCollectorResult(ownPlatform, 100.0),
                    createCollectorResult(foreignPlatform, 100.0))));
    ExerciseComposer.Composer simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withSecurityCoverage(
                securityCoverageComposer.forSecurityCoverage(
                    SecurityCoverageFixture.createSecurityCoverageWithDomainObjects(
                        List.of(technique.get()), List.of())))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectorContract(
                        injectorContractComposer
                            .forInjectorContract(
                                InjectorContractFixture.createDefaultInjectorContract())
                            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                            .withAttackPattern(technique))
                    .withExpectation(detection));
    simulation.get().setStatus(ExerciseStatus.FINISHED);
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
        .withSimulation(simulation)
        .persist();
    entityManager.flush();
    entityManager.clear();
    return simulation.get();
  }

  /** Builds the bundle of the simulation inside the given scope, as the coverage job does. */
  private Bundle buildBundleInScope(Exercise simulation, TxCtx scope) throws Exception {
    tenantTx.setScopeOnCurrentTransaction(scope);
    Exercise scopedSimulation = exerciseRepository.findById(simulation.getId()).orElseThrow();
    SecurityCoverageSendJob job =
        securityCoverageSendJobService
            .createOrUpdateCoverageSendJobForSimulationIfReady(scopedSimulation)
            .orElseThrow();
    return securityCoverageService.createBundleFromSendJobs(List.of(job));
  }

  private String coveragePlatformsJson(Bundle bundle) {
    StixRefToExternalRef ref =
        securityCoverageComposer.generatedItems.getFirst().getAttackPatternRefs().iterator().next();
    RelationshipObject sro =
        bundle.findRelationshipsByTargetRef(new Identifier(ref.getStixRef())).getFirst();
    return sro.getProperty(ExtendedProperties.COVERAGE_PLATFORMS.toString())
        .toStix(mapper)
        .toString();
  }

  private String detectionEntryJson(SecurityPlatform... platforms) {
    return new io.openaev.stix.types.List<>(
            java.util.Arrays.stream(platforms)
                .map(
                    platform ->
                        new Complex<>(
                            new PlatformCoverageResult(
                                new Identifier(ObjectTypes.IDENTITY.toString(), platform.getId())
                                    .getValue(),
                                "DETECTION",
                                100)))
                .toList())
        .toStix(mapper)
        .toString();
  }

  @Test
  @DisplayName("given a result from another tenant's platform should never reference that platform")
  void given_resultFromAnotherTenantsPlatform_should_neverReferenceThatPlatform() throws Exception {
    // Arrange
    String foreignTenantId =
        tenantHelper.createTenant("sec-cov-platform-foreign-" + UUID.randomUUID()).getId();
    SecurityPlatform foreignPlatform = SecurityPlatformFixture.createDefault("Foreign EDR", "EDR");
    foreignPlatform.setId(seedSecurityPlatform(foreignTenantId, foreignPlatform.getName()));
    SecurityPlatform ownPlatform =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("Own EDR", "EDR"))
            .persist()
            .get();
    Exercise simulation = seedSimulationWithResultsOfBothTenants(ownPlatform, foreignPlatform);

    // Act
    Bundle bundle = buildBundleInScope(simulation, TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));

    // Assert: the foreign platform exists, the scope hides it, and the bundle never names it
    Long foreignRows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM assets WHERE asset_id = ?", Long.class, foreignPlatform.getId());
    assertThat(foreignRows).isEqualTo(1L);
    assertThat(bundle.toStix(mapper).toString()).doesNotContain(foreignPlatform.getId());
    assertThatJson(coveragePlatformsJson(bundle)).isEqualTo(detectionEntryJson(ownPlatform));
    assertThat(
            bundle.findById(new Identifier(ObjectTypes.IDENTITY.toString(), ownPlatform.getId())))
        .isNotNull();
  }

  @Test
  @DisplayName("given a scope covering both tenants should attribute both platforms")
  void given_scopeCoveringBothTenants_should_attributeBothPlatforms() throws Exception {
    // Arrange: same data, only the scope differs, so the scope is what hides the foreign platform
    String foreignTenantId =
        tenantHelper.createTenant("sec-cov-platform-foreign-" + UUID.randomUUID()).getId();
    SecurityPlatform foreignPlatform = SecurityPlatformFixture.createDefault("Foreign EDR", "EDR");
    foreignPlatform.setId(seedSecurityPlatform(foreignTenantId, foreignPlatform.getName()));
    SecurityPlatform ownPlatform =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("Own EDR", "EDR"))
            .persist()
            .get();
    Exercise simulation = seedSimulationWithResultsOfBothTenants(ownPlatform, foreignPlatform);

    // Act
    Bundle bundle =
        buildBundleInScope(
            simulation, TxCtx.forTenants(List.of(Tenant.DEFAULT_TENANT_UUID, foreignTenantId)));

    // Assert
    assertThatJson(coveragePlatformsJson(bundle))
        .when(Option.IGNORING_ARRAY_ORDER)
        .isEqualTo(detectionEntryJson(ownPlatform, foreignPlatform));
  }
}
