package io.openaev.rest.attack_pattern.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.exception.TenantWriteScopeException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link AttackPatternService#findOrCreate} is a read-before-write keyed on the MITRE external id,
 * which is unique per tenant and not globally. Under a scope that pins one tenant it must create
 * one row per tenant for the same external id; under a broader scope it must refuse rather than
 * adopt whichever tenant's row the lookup happened to return first.
 *
 * <p>NOT {@code @Transactional}: {@link TenantScopedTransaction#execute} refuses to open inside an
 * already-active transaction, and the point here is that each call runs in its own scoped
 * transaction, the way the payload import reaches it. Cleanup goes through an auto-committing
 * {@link JdbcTemplate}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=attack_patterns")
@WithMockUser(isAdmin = true)
@DisplayName("findOrCreate attributes the attack pattern from the request write scope")
class AttackPatternFindOrCreateScopeTest extends IntegrationTest {

  private static final String EXTERNAL_ID = "TA9850";

  @Autowired private AttackPatternService attackPatternService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void createTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("ap-foc-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("ap-foc-b").getId();
  }

  @AfterEach
  void cleanUp() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("DELETE FROM attack_patterns WHERE attack_pattern_external_id = ?", EXTERNAL_ID);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Nested
  @DisplayName("Single-tenant scope")
  class SingleTenantScope {

    @Test
    @DisplayName("given the same external id in two tenants, should create one row per tenant")
    void given_sameExternalIdInTwoTenants_should_createOneRowPerTenant() {
      // Arrange / Act
      String idA = findOrCreateIn(tenantA);
      String idB = findOrCreateIn(tenantB);

      // Assert: two distinct rows, each attributed to the tenant that asked for it.
      assertEquals(tenantA, storedTenant(idA), "A's pattern must belong to A");
      assertEquals(tenantB, storedTenant(idB), "B's pattern must belong to B");
      assertNotEquals(idA, idB, "B must not adopt A's row for the same external id");
    }

    @Test
    @DisplayName("given an existing row in the same tenant, should return it instead of creating")
    void given_existingRowInSameTenant_should_returnIt() {
      // Arrange
      String first = findOrCreateIn(tenantA);

      // Act
      String second = findOrCreateIn(tenantA);

      // Assert
      assertEquals(first, second, "a second call in the same tenant must reuse the row");
    }
  }

  @Nested
  @DisplayName("Ambiguous scope")
  class AmbiguousScope {

    @Test
    @DisplayName("given a scope holding two tenants, should refuse the write")
    void given_multiTenantScope_should_refuseTheWrite() {
      // Arrange
      TxCtx ambiguous = TxCtx.forTenants(java.util.List.of(tenantA, tenantB));

      // Act / Assert
      assertThrows(
          TenantWriteScopeException.class,
          () ->
              tenantTx.execute(
                  ambiguous, () -> attackPatternService.findOrCreate(ambiguous, input())),
          "a two-tenant scope cannot attribute the row, so the write must be refused");
    }
  }

  private String findOrCreateIn(String tenantId) {
    TxCtx ctx = TxCtx.forTenant(tenantId);
    AttackPattern created =
        tenantTx.execute(ctx, () -> attackPatternService.findOrCreate(ctx, input()));
    return created.getId();
  }

  private static AttackPatternCreateInput input() {
    AttackPatternCreateInput input = new AttackPatternCreateInput();
    input.setName("find-or-create-" + EXTERNAL_ID);
    input.setExternalId(EXTERNAL_ID);
    input.setStixId("attack-pattern--" + UUID.randomUUID());
    return input;
  }

  private String storedTenant(String attackPatternId) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT tenant_id FROM attack_patterns WHERE attack_pattern_id = ?",
            String.class,
            attackPatternId);
  }
}
