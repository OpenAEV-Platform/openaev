package io.openaev.rest.atomic_testing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = false)
@DisplayName("A non-admin reading a simulation inject gets a resolved inject_type")
class AtomicTestingApiNonAdminTenantScopeTest extends IntegrationTest {

  private static final String DETAIL = "/api/tenants/{tenantId}/atomic-testings/{injectId}";
  private static final Set<Capability> ACCESS_ASSESSMENT = Set.of(Capability.ACCESS_ASSESSMENT);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;

  private String tenantA;
  private String injectId;
  private String injectorId;
  private String injectorContractId;
  private String expectedInjectType;

  @BeforeEach
  void seedSimulationInjectInTenant() throws Exception {
    tenantA =
        tenantHelper.createTenantWithCapabilities("at-rbac-scope-a", ACCESS_ASSESSMENT).getId();

    inTenant(
        tenantA,
        () -> {
          InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
          injectorContractId = contract.getId();
          injectorId = contract.getFirstInjector().getId();
          expectedInjectType = contract.getFirstInjector().getType();

          Inject inject = InjectFixture.getInjectWithoutContract();
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(
                  injectComposer
                      .forInject(inject)
                      .withInjectorContract(injectorContractComposer.forInjectorContract(contract)))
              .persist();
          injectId = inject.getId();
          return null;
        });
  }

  @AfterEach
  void cleanup() {
    if (injectId != null) {
      jdbc.update("DELETE FROM injects WHERE inject_id = ?", injectId);
    }
    if (tenantA != null) {
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantA);
      jdbc.update(
          "DELETE FROM injectors_injector_contracts WHERE injector_contract_id = ?",
          injectorContractId);
      jdbc.update(
          "DELETE FROM injectors_contracts WHERE injector_contract_id = ?", injectorContractId);
      jdbc.update("DELETE FROM injectors WHERE injector_id = ?", injectorId);
      tenantHelper.deleteCommittedTenants(tenantA);
    }
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("inject_type resolves through the v2-scoped injectors table, it is not served null")
  void nonAdminSimulationInjectReadResolvesInjectType() throws Exception {
    mvc.perform(get(DETAIL, tenantA, injectId).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.inject_type").value(expectedInjectType));
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }
}
