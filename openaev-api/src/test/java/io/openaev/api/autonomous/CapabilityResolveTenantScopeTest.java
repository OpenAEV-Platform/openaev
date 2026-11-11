package io.openaev.api.autonomous;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.api.autonomous.dto.CapabilityQueryInput;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The capability resolver reads the contracts of the request's whole scope. A built-in contract id
 * repeats in every tenant that registers its injector, so a caller of several tenants with no
 * selector reads each id once per tenant; the resolver lists a contract id once per technique.
 *
 * <p>Deliberately NOT {@code @Transactional}: the rows of both tenants have to be committed before
 * the request reads them, and the endpoint opens its own transaction.
 */
@WithMockUser(isAdmin = true)
@DisplayName("POST /autonomous-runs/capabilities/resolve for a caller of several tenants")
class CapabilityResolveTenantScopeTest extends ProductionTablesIntegrationTest {

  private static final String PATTERN_A = "T9101.001";
  private static final String PATTERN_B = "T9102.002";
  private static final String PATTERN_SHARED = "T9103.003";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private EntityManager entityManager;

  // The endpoint is EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private final String sharedContractId = UUID.randomUUID().toString();
  private final String sharedInjectorId = UUID.randomUUID().toString();
  private final String sharedInjectorType = "cap-rank-" + UUID.randomUUID();
  private final List<String> injectorIds = new ArrayList<>();
  private final List<String> patternIds = new ArrayList<>();
  private String tenantAId;
  private String tenantBId;

  @BeforeEach
  void seedTheSameContractIdInTwoTenants() throws Exception {
    tenantAId = tenantBelowTheDefaultTenant();
    tenantBId = tenantHelper.createTenantWithCurrentUser("cap-resolve-b").getId();
    // The copy of the higher tenant id is written first, so the order the rows come back in is not
    // the ranking order
    if (tenantAId.compareTo(tenantBId) > 0) {
      seedContract(tenantAId, PATTERN_A, PATTERN_SHARED);
      seedContract(tenantBId, PATTERN_B, PATTERN_SHARED);
    } else {
      seedContract(tenantBId, PATTERN_B, PATTERN_SHARED);
      seedContract(tenantAId, PATTERN_A, PATTERN_SHARED);
    }
  }

  /**
   * The default tenant id is neither the lowest nor the highest of the ids: a tenant below it makes
   * "the default tenant's copy" and "the lowest tenant's copy" two different copies.
   */
  private String tenantBelowTheDefaultTenant() throws Exception {
    for (int attempt = 0; attempt < 80; attempt++) {
      String tenantId = tenantHelper.createTenantWithCurrentUser("cap-resolve-a").getId();
      if (tenantId.compareTo(Tenant.DEFAULT_TENANT_UUID) < 0) {
        return tenantId;
      }
      tenantHelper.deleteCommittedTenants(tenantId);
    }
    throw new IllegalStateException("no tenant id below the default tenant id");
  }

  @AfterEach
  void cleanup() {
    cleanupTenant(tenantAId);
    cleanupTenant(tenantBId);
    cleanupTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantHelper.detachCurrentUserFromTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantHelper.deleteCommittedTenants(tenantAId, tenantBId);
    TenantContext.clearCurrentTenant();
  }

  private JsonNode resolve(String technique) throws Exception {
    CapabilityQueryInput input = new CapabilityQueryInput();
    input.setTechniques(List.of(technique));
    return resolve(input);
  }

  private JsonNode resolve(CapabilityQueryInput input) throws Exception {
    // Act: no header, so the scope is the caller's whole membership while the ambient tenant stays
    // the default one
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    String body =
        mvc.perform(
                post(AUTONOMOUS_URI + "/capabilities/resolve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private List<String> candidatesOf(String technique) throws Exception {
    List<String> ids = new ArrayList<>();
    for (JsonNode contract : resolve(technique).get("resolutions").get(0).get("contracts")) {
      ids.add(contract.get("injector_contract_id").asText());
    }
    return ids;
  }

  private List<String> candidateLabelsOf(String technique) throws Exception {
    List<String> labels = new ArrayList<>();
    for (JsonNode contract : resolve(technique).get("resolutions").get(0).get("contracts")) {
      labels.add(contract.get("label").asText());
    }
    return labels;
  }

  private List<String> sharedContractLabelsIn(JsonNode resolution) {
    List<String> labels = new ArrayList<>();
    for (JsonNode contract : resolution.get("contracts")) {
      if (sharedContractId.equals(contract.get("injector_contract_id").asText())) {
        labels.add(contract.get("label").asText());
      }
    }
    return labels;
  }

  private List<String> installedNamesOfSharedInjector() throws Exception {
    List<String> names = new ArrayList<>();
    for (JsonNode injector : resolve(PATTERN_SHARED).get("arsenal").get("installed_injectors")) {
      if (sharedInjectorType.equals(injector.get("injector_type").asText())) {
        names.add(injector.get("name").asText());
      }
    }
    return names;
  }

  private static String labelOf(String tenantId) {
    return "copy-of-" + tenantId;
  }

  private static String injectorNameOf(String tenantId) {
    return "injector-of-" + tenantId;
  }

  private String lowerOfTheTwoTenants() {
    return tenantAId.compareTo(tenantBId) < 0 ? tenantAId : tenantBId;
  }

  @Test
  @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_listTheSharedContractOncePerTechnique")
  void given_callerOfTwoTenantsAndNoHeader_should_listTheSharedContractOncePerTechnique()
      throws Exception {
    // Act
    List<String> candidates = candidatesOf(PATTERN_SHARED);

    // Assert
    assertThat(candidates).containsExactly(sharedContractId);
  }

  @Test
  @DisplayName("given_techniqueOfOneCopy_should_stillResolveTheContract")
  void given_techniqueOfOneCopy_should_stillResolveTheContract() throws Exception {
    // Act
    List<String> candidatesOfA = candidatesOf(PATTERN_A);
    List<String> candidatesOfB = candidatesOf(PATTERN_B);

    // Assert: a technique mapped in a single tenant's copy is not lost by reading one per id,
    // whichever copy the scope lists first
    assertThat(candidatesOfA).containsExactly(sharedContractId);
    assertThat(candidatesOfB).containsExactly(sharedContractId);
  }

  @Test
  @DisplayName("given_copiesOfTwoTenants_should_resolveTheContractFromTheLowestTenantsCopy")
  void given_copiesOfTwoTenants_should_resolveTheContractFromTheLowestTenantsCopy()
      throws Exception {
    // Act
    List<String> labels = candidateLabelsOf(PATTERN_SHARED);

    // Assert
    assertThat(labels).containsExactly(labelOf(lowerOfTheTwoTenants()));
  }

  @Test
  @DisplayName("given_copyInTheDefaultTenant_should_resolveTheContractFromTheDefaultTenantsCopy")
  void given_copyInTheDefaultTenant_should_resolveTheContractFromTheDefaultTenantsCopy()
      throws Exception {
    // Arrange: the default tenant joins the caller's scope, its copy is written last
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    seedContract(Tenant.DEFAULT_TENANT_UUID, PATTERN_SHARED);

    // Act
    List<String> labels = candidateLabelsOf(PATTERN_SHARED);

    // Assert
    assertThat(labels).containsExactly(labelOf(Tenant.DEFAULT_TENANT_UUID));
  }

  @Test
  @DisplayName("given_injectorOfTwoTenants_should_listItOnceFromTheLowestTenantsCopy")
  void given_injectorOfTwoTenants_should_listItOnceFromTheLowestTenantsCopy() throws Exception {
    // Act
    List<String> names = installedNamesOfSharedInjector();

    // Assert
    assertThat(names).containsExactly(injectorNameOf(lowerOfTheTwoTenants()));
  }

  @Test
  @DisplayName("given_injectorInTheDefaultTenant_should_listItOnceFromTheDefaultTenantsCopy")
  void given_injectorInTheDefaultTenant_should_listItOnceFromTheDefaultTenantsCopy()
      throws Exception {
    // Arrange
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    seedContract(Tenant.DEFAULT_TENANT_UUID, PATTERN_SHARED);

    // Act
    List<String> names = installedNamesOfSharedInjector();

    // Assert
    assertThat(names).containsExactly(injectorNameOf(Tenant.DEFAULT_TENANT_UUID));
  }

  @Test
  @DisplayName(
      "given_copiesOnDifferentPlatformsAndAPlatformFilter_should_resolveFromTheCopyOnThatPlatform")
  void given_copiesOnDifferentPlatformsAndAPlatformFilter_should_resolveFromTheCopyOnThatPlatform()
      throws Exception {
    // Arrange: the best-ranked copy runs on Windows only, the other copy on Linux only, and both
    // carry the technique and the output type
    String lower = lowerOfTheTwoTenants();
    String higher = lower.equals(tenantAId) ? tenantBId : tenantAId;
    setPlatformAndOutputOf(lower, Endpoint.PLATFORM_TYPE.Windows);
    setPlatformAndOutputOf(higher, Endpoint.PLATFORM_TYPE.Linux);
    CapabilityQueryInput input = new CapabilityQueryInput();
    input.setTechniques(List.of(PATTERN_SHARED));
    input.setOutputTypes(List.of(SHARED_OUTPUT_TYPE.getLabel()));
    input.setPlatforms(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));

    // Act
    JsonNode resolutions = resolve(input).get("resolutions");

    // Assert
    assertThat(sharedContractLabelsIn(resolutions.get(0))).containsExactly(labelOf(higher));
    assertThat(sharedContractLabelsIn(resolutions.get(1))).containsExactly(labelOf(higher));
  }

  private static final ContractOutputType SHARED_OUTPUT_TYPE = ContractOutputType.PortsScan;

  private void setPlatformAndOutputOf(String tenantId, Endpoint.PLATFORM_TYPE platform) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          InjectorContract contract =
              injectorContractRepository
                  .findById(new InjectorContractId(sharedContractId, tenantId))
                  .orElseThrow();
          contract.setPlatforms(new Endpoint.PLATFORM_TYPE[] {platform});
          ObjectNode content = contract.getConvertedContent().deepCopy();
          content.putArray("outputs").addObject().put("type", SHARED_OUTPUT_TYPE.getLabel());
          contract.setContent(content.toString());
          contract.setConvertedContent(content);
          injectorContractRepository.save(contract);
          entityManager.flush();
        });
  }

  private void seedContract(String tenantId, String... patternExternalIds) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setId(sharedInjectorId);
          injector.setType(sharedInjectorType);
          injector.setName(injectorNameOf(tenantId));
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          injectorIds.add(injectorRepository.save(injector).getId());

          List<AttackPattern> savedPatterns = new ArrayList<>();
          for (String patternExternalId : patternExternalIds) {
            AttackPattern pattern =
                AttackPatternFixture.createAttackPatternsWithExternalId(patternExternalId);
            pattern.setTenant(new Tenant(tenantId));
            AttackPattern savedPattern = attackPatternRepository.save(pattern);
            patternIds.add(savedPattern.getId());
            savedPatterns.add(savedPattern);
          }

          InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
          contract.setId(sharedContractId);
          contract.setLabels(Map.of("en", labelOf(tenantId)));
          contract.setTenant(new Tenant(tenantId));
          contract.clearInjectors();
          contract.addInjector(injector);
          contract.setAttackPatterns(new ArrayList<>(savedPatterns));
          injectorContractRepository.save(contract);
          entityManager.flush();
        });
  }

  private void cleanupTenant(String tenantId) {
    if (tenantId == null) {
      return;
    }
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          entityManager
              .createNativeQuery(
                  "DELETE FROM injectors_injector_contracts WHERE injector_contract_id ="
                      + " :contractId AND tenant_id = :tenantId")
              .setParameter("contractId", sharedContractId)
              .setParameter("tenantId", tenantId)
              .executeUpdate();
          injectorContractRepository
              .findById(new InjectorContractId(sharedContractId, tenantId))
              .ifPresent(injectorContractRepository::delete);
          entityManager.flush();
        });
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          injectorIds.forEach(injectorRepository::deleteByInjectorId);
          patternIds.forEach(
              patternId ->
                  attackPatternRepository
                      .findById(patternId)
                      .ifPresent(attackPatternRepository::delete));
        });
  }
}
