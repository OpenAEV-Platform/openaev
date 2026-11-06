package io.openaev.rest.injector_contract;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.exception.TenantSingleScopeRequiredException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The caller shapes of the singular endpoints that read one contract: the contract GET and the
 * threat arsenal GET, which carry the same {@code @RequireTenantSelector} and {@code
 * requireSingleTenant}. The update, mapping and delete endpoints of the contract share both, so the
 * shapes are the same for them; the table in the pull request lists the five.
 *
 * <p>Deliberately NOT {@code @Transactional}: the HTTP call must run in its own transaction with
 * its own tenant scope, the way a real request does.
 */
@DisplayName("Singular contract endpoints, the shapes of caller that carry no default tenant")
@WithMockUser(isAdmin = true)
class InjectorContractApiSingularEndpointsCallerShapesTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private final List<String> createdTenants = new ArrayList<>();

  @AfterEach
  void cleanup() {
    createdTenants.forEach(tenantHelper::deleteCommittedTenants);
  }

  static Stream<String> singularUrls() {
    return Stream.of(INJECTOR_CONTRACT_URL, THREAT_ARSENAL_URL);
  }

  private String tenantWithTheBuiltinContract(String name) throws Exception {
    String tenantId = tenantHelper.createTenantWithCurrentUser(name).getId();
    createdTenants.add(tenantId);
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantId);
    return tenantId;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("singularUrls")
  @DisplayName("given_singleTenantCallerAndNoSelector_should_answerWithItsTenantCopy")
  void given_singleTenantCallerAndNoSelector_should_answerWithItsTenantCopy(String url)
      throws Exception {
    // Arrange: the caller's only tenant is not the default one.
    tenantWithTheBuiltinContract("ic-shape-single");

    // Act
    MvcResult result =
        mvc.perform(get(url + "/" + ManualContract.MANUAL_DEFAULT).with(csrf())).andReturn();

    // Assert
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("singularUrls")
  @DisplayName("given_twoTenantCallerWithoutTheDefaultAndNoSelector_should_refuseWithSelectorCode")
  void given_twoTenantCallerWithoutTheDefaultAndNoSelector_should_refuseWithSelectorCode(String url)
      throws Exception {
    // Arrange: two tenants, neither is the default one, both hold the built-in contract.
    tenantWithTheBuiltinContract("ic-shape-multi-a");
    tenantWithTheBuiltinContract("ic-shape-multi-b");

    // Act
    MvcResult result =
        mvc.perform(get(url + "/" + ManualContract.MANUAL_DEFAULT).with(csrf())).andReturn();

    // Assert
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString()).contains("TENANT_SELECTOR_REQUIRED");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("singularUrls")
  @DisplayName("given_callerWithNoMembership_should_refuseAndReadNothing")
  void given_callerWithNoMembership_should_refuseAndReadNothing(String url) throws Exception {
    // Arrange: the caller belongs to no tenant at all.
    // Act
    MvcResult result =
        mvc.perform(get(url + "/" + ManualContract.MANUAL_DEFAULT).with(csrf())).andReturn();

    // Assert
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .contains(TenantSingleScopeRequiredException.CODE);
  }
}
