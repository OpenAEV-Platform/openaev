package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.rest.injector_contract.InjectorContractApi;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A many-to-many link row is written by Hibernate's collection actions, which raise no entity
 * event: nothing is captured at ask time for the join table, and its composite key means the
 * trigger emits no row id either. The write can then only be attributed from the flush stack, and
 * flushed from a test frame it is waived as test-driven, although the request that asked for it is
 * known: the owner of the collection was captured at {@code merge}.
 *
 * <p>The production shape is the contract update on the non-prefixed route: the contract (default
 * tenant, found through the ambient context) gets new attack patterns while the request scope is
 * tenant B, and the {@code injectors_contracts_attack_patterns} rows carry the contract's tenant.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName(
    "Write-attribution: a join-table row is attributed to the request that changed its owner")
class WriteAttrJoinTableAttributionTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String JOIN_TABLE = "injectors_contracts_attack_patterns";
  private static final String CONTROLLER =
      "io.openaev.rest.injector_contract.InjectorContractApi.updateInjectorContract";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;

  private String tenantB;
  private String contractId;
  private String attackPatternId;

  @BeforeEach
  void setUp() throws Exception {
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-join-b").getId();
    // The contract and the attack pattern live in the default tenant (no scope is set while they
    // are seeded, so the trigger stays silent on the seed).
    // A fresh injector rather than the well-known one: under the production active-tables list
    // the well-known lookup is scoped and finds nothing, and this test only needs an owner row.
    Injector injector =
        InjectorFixture.createInjector(
            "wattr-join-" + UUID.randomUUID(), "wattr join injector", "wattr-join-type");
    entityManager.persist(injector);
    InjectorContract contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injector)
            .persist()
            .get();
    AttackPattern attackPattern =
        attackPatternComposer
            .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
            .persist()
            .get();
    entityManager.flush();
    entityManager.clear();
    contractId = contract.getId();
    attackPatternId = attackPattern.getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("Given a header-route update that adds elements to a tenant-keyed collection")
  class CollectionWrite {

    @Test
    @DisplayName(
        "Given the link rows flushed later from a test frame, should attribute them to the controller")
    void given_joinRowsFlushedFromTestFrame_should_attributeToTheController() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      InjectorContractUpdateInput input = new InjectorContractUpdateInput();
      input.setContent("{}");
      input.setAttackPatternsIds(List.of(attackPatternId));
      input.setDomains(new HashSet<>());

      // Act
      mvc.perform(
              put(InjectorContractApi.INJECTOR_CONTRACT_URL + "/" + contractId)
                  .header("X-Tenant-Ids", tenantB)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(input)))
          .andExpect(status().isOk());
      // No explicit flush: the pending link insert is forced out by a native read from this test
      // method, so the flush stack holds no production frame.
      String linkTenant = readLinkTenantFromTestFrame();
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(
          DEFAULT_TENANT, linkTenant, "precondition: the link row carries the contract's tenant");
      List<Violation> linkViolations =
          WriteAttrDetectorRecorder.violations().stream()
              .filter(v -> JOIN_TABLE.equals(v.table()))
              .toList();
      assertFalse(linkViolations.isEmpty(), "the trigger must see the link-row write");
      List<String> attributed =
          WriteAttrGateExtension.offendingSignatures(
              WriteAttrDetectorRecorder.violations(), Set.of());
      assertTrue(
          attributed.contains(JOIN_TABLE + " DEFAULT " + CONTROLLER),
          "the link row must be attributed to the request that changed its owner, not waived;"
              + " attributed="
              + attributed
              + " violations="
              + linkViolations);
    }
  }

  /**
   * Forces the pending flush from this test method with a native query the inspector leaves alone
   * (no table), so the flush stack holds no production frame, then reads the link row through the
   * transaction's JDBC connection, which the statement inspector does not rewrite.
   */
  private String readLinkTenantFromTestFrame() {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM injectors_contracts_attack_patterns"
            + " WHERE injector_contract_id = ? AND attack_pattern_id = ?",
        String.class,
        contractId,
        attackPatternId);
  }
}
