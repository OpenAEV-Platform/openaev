package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /injector_contracts} is served by the native query at {@code
 * InjectorContractRepository#getAllRawInjectorsContracts}. That query scopes the contract rows and
 * now also correlates {@code injectors_contracts_attack_patterns} on {@code tenant_id}; this class
 * is the regression cover for why that correlation has to stay, not a description of a current
 * defect.
 *
 * <p>Contract ids are shared across tenants by design: {@code InjectorContract}'s primary key is
 * {@code (injector_contract_id, tenant_id)} and every tenant registers the same built-in contract
 * declarations, so the same id exists in every tenant. A join that drops {@code tenant_id} would
 * therefore pick up another tenant's mapping rows for the same contract id, which is what these
 * tests fail on if the correlation is removed.
 *
 * <p>Deliberately NOT {@code @Transactional}: the rows of both tenants have to be committed before
 * the request reads them, and the endpoint opens its own transaction.
 */
@WithMockUser(isAdmin = true)
@DisplayName("GET /injector_contracts attack pattern aggregation across tenants")
class InjectorContractRawAttackPatternJoinTest extends IntegrationTest {

  private static final String PATTERN_A = "T9001.001";
  private static final String PATTERN_B = "T9002.002";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private EntityManager entityManager;

  private final String sharedContractId = UUID.randomUUID().toString();
  private final List<String> injectorIds = new ArrayList<>();
  private final List<String> patternIds = new ArrayList<>();
  private String tenantAId;
  private String tenantBId;

  @BeforeEach
  void seedTheSameContractIdInTwoTenants() throws Exception {
    tenantAId = tenantHelper.createTenantWithCurrentUser("ic-raw-join-a").getId();
    tenantBId = tenantHelper.createTenantWithCurrentUser("ic-raw-join-b").getId();
    seedContract(tenantAId, PATTERN_A);
    seedContract(tenantBId, PATTERN_B);
  }

  @AfterEach
  void cleanup() {
    cleanupTenant(tenantAId);
    cleanupTenant(tenantBId);
    // The class is not @Transactional, so the two tenants and their membership rows are committed.
    // Deleting only the test entities would leave them in the shared integration database for every
    // later class, so the onboarded tenants go too, and the ambient tenant is cleared after them.
    tenantHelper.deleteCommittedTenants(tenantAId, tenantBId);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("A tenant's contract only aggregates the attack patterns mapped in that same tenant")
  void given_theSameContractIdInTwoTenants_should_onlyAggregateOwnAttackPatterns()
      throws Exception {
    // Act
    String body =
        mvc.perform(get("/api/tenants/{tenantId}/injector_contracts", tenantAId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    List<String> aggregated = attackPatternsOf(body, sharedContractId);
    assertThat(aggregated).containsExactly(PATTERN_A);
  }

  private List<String> attackPatternsOf(String body, String contractId) throws Exception {
    JsonNode root = mapper.readTree(body);
    List<String> patterns = new ArrayList<>();
    for (JsonNode contract : root) {
      if (contractId.equals(contract.get("injector_contract_id").asText())) {
        contract
            .get("injector_contract_attack_patterns_external_id")
            .forEach(pattern -> patterns.add(pattern.asText()));
      }
    }
    return patterns;
  }

  private void seedContract(String tenantId, String patternExternalId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          injectorIds.add(injectorRepository.save(injector).getId());

          AttackPattern pattern =
              AttackPatternFixture.createAttackPatternsWithExternalId(patternExternalId);
          pattern.setTenant(new Tenant(tenantId));
          AttackPattern savedPattern = attackPatternRepository.save(pattern);
          patternIds.add(savedPattern.getId());

          InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
          contract.setId(sharedContractId);
          contract.setTenant(new Tenant(tenantId));
          contract.clearInjectors();
          contract.addInjector(injector);
          contract.setAttackPatterns(new ArrayList<>(List.of(savedPattern)));
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
