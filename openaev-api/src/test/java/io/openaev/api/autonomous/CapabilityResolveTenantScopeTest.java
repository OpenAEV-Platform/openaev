package io.openaev.api.autonomous;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.dto.CapabilityQueryInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class CapabilityResolveTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

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
  private final List<String> injectorIds = new ArrayList<>();
  private final List<String> patternIds = new ArrayList<>();
  private String tenantAId;
  private String tenantBId;

  @BeforeEach
  void seedTheSameContractIdInTwoTenants() throws Exception {
    tenantAId = tenantHelper.createTenantWithCurrentUser("cap-resolve-a").getId();
    tenantBId = tenantHelper.createTenantWithCurrentUser("cap-resolve-b").getId();
    seedContract(tenantAId, PATTERN_A, PATTERN_SHARED);
    seedContract(tenantBId, PATTERN_B, PATTERN_SHARED);
  }

  @AfterEach
  void cleanup() {
    cleanupTenant(tenantAId);
    cleanupTenant(tenantBId);
    tenantHelper.deleteCommittedTenants(tenantAId, tenantBId);
    TenantContext.clearCurrentTenant();
  }

  private List<String> candidatesOf(String technique) throws Exception {
    CapabilityQueryInput input = new CapabilityQueryInput();
    input.setTechniques(List.of(technique));
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
    List<String> ids = new ArrayList<>();
    for (JsonNode contract : mapper.readTree(body).get("resolutions").get(0).get("contracts")) {
      ids.add(contract.get("injector_contract_id").asText());
    }
    return ids;
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

  private void seedContract(String tenantId, String... patternExternalIds) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Injector injector = InjectorFixture.createDefaultPayloadInjector();
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
