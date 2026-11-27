package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RowTenantScope;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The owner lookup of injects ({@code findTenantIdsByInjectIds}) reads an active table, so it is
 * narrowed to the tenant scope it runs in: under one tenant's scope an inject of another tenant is
 * absent, exactly like an unknown one. The batched callback consumer groups a batch that mixes
 * tenants, so it reads the owners under {@code TxCtx.allTenants()}, where every inject resolves to
 * its own tenant.
 *
 * <p>The production active tables are armed, so the class follows what production enforces.
 *
 * <p>Deliberately NOT {@code @Transactional}: each read runs in a transaction of its own, under the
 * scope it states. The rows are committed in their tenant and removed with it.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Owner lookup of injects")
class InjectTenantProjectionTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String UNKNOWN_INJECT_ID = "does-not-exist";

  @Autowired private InjectRepository injectRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private RowTenantScope rowTenantScope;

  private String tenantA;
  private String tenantB;
  private String injectA;
  private String injectB;

  @BeforeEach
  void seedOneInjectInEachOfTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("proj-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("proj-b").getId();
    injectA = seedInjectInTenant(tenantA);
    injectB = seedInjectInTenant(tenantB);
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void deleteTheTenants() {
    TenantContext.clearCurrentTenant();
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Nested
  @DisplayName("Read in the scope of one tenant")
  class OneTenantScope {

    @Test
    @DisplayName("given the scope of one tenant should resolve only the injects of that tenant")
    void given_theScopeOfOneTenant_should_resolveOnlyTheInjectsOfThatTenant() {
      // Act
      Map<String, String> tenantByInjectId = ownersUnder(TxCtx.forTenant(tenantB));

      // Assert
      assertThat(tenantByInjectId).containsExactlyEntriesOf(Map.of(injectB, tenantB));
    }
  }

  @Nested
  @DisplayName("Read across tenants")
  class AllTenantsScope {

    @Test
    @DisplayName("given all tenants should resolve the owning tenant of every known inject")
    void given_allTenants_should_resolveTheOwningTenantOfEveryKnownInject() {
      // Act
      Map<String, String> tenantByInjectId = ownersUnder(TxCtx.allTenants());

      // Assert
      assertThat(tenantByInjectId)
          .containsExactlyInAnyOrderEntriesOf(Map.of(injectA, tenantA, injectB, tenantB));
    }
  }

  private Map<String, String> ownersUnder(TxCtx scope) {
    return tenantTx.execute(
        scope,
        () ->
            injectRepository
                .findTenantIdsByInjectIds(List.of(injectA, injectB, UNKNOWN_INJECT_ID))
                .stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (String) row[1])));
  }

  private String seedInjectInTenant(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    String[] injectId = new String[1];
    rowTenantScope.write(
        tenantId,
        () -> {
          InjectorContract contract = injectorContractFixture.getWellKnownSingleEmailContract();
          InjectComposer.Composer injectC =
              injectComposer.forInject(
                  InjectFixture.createInject(contract, "callback-tenant-probe"));
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInjects(List.of(injectC))
              .persist();
          injectId[0] = injectC.get().getId();
        });
    return injectId[0];
  }
}
