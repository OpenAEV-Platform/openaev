package io.openaev.api.threat_arsenal;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * With {@code injectors} activated, creating a threat arsenal action (payload) under a tenant path
 * must still resolve the tenant's payload-supporting injectors and link the new contract to them.
 * Regression test for a gap found manually: none of {@link ThreatArsenalApi}'s handlers carry a
 * {@code TxCtx}, so {@code PayloadService#synchroniseInjectorContractBasedOnPayload}'s read of the
 * v2-scoped {@code injectors} table (via {@code InjectorRepository#findAllByPayloads}) sees zero
 * rows and bails out before ever creating the injector contract - the payload is saved, but no
 * threat-arsenal entry (injector contract) appears for it.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = true)
@DisplayName(
    "ThreatArsenalApi scopes the payload-supporting injector lookup to the caller's tenant")
class ThreatArsenalApiTenantIsolationTest extends IntegrationTest {

  private static final String CREATE = "/api/tenants/{tenantId}/threat_arsenals";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;

  private String tenantA;

  @BeforeEach
  void seedTenantWithPayloadSupportingInjector() throws Exception {
    // Tenant onboarding already registers the built-in (payload-supporting) injector for the new
    // tenant - see TenantIsolationTestHelper's javadoc.
    tenantA = tenantHelper.createTenantWithCurrentUser("ta-iso-a").getId();
  }

  @Test
  @DisplayName(
      "creating a command-line action under tenant A's path links the contract to A's injector")
  void creatingActionUnderTenantPathLinksContractToTenantInjector() throws Exception {
    ThreatArsenalActionCreateInput input =
        ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of());

    String response =
        mvc.perform(
                post(CREATE, tenantA)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response, "the created action must not be null");
    String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
    Payload payload = payloadRepository.findById(payloadId).orElse(null);
    assertNotNull(payload, "the payload itself must be saved");

    InjectorContract contract =
        injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
    assertNotNull(
        contract,
        "the injector contract must be created so the action shows up in the threat arsenal");
    assertThat(contract.getInjectors())
        .as("the contract must be linked to the tenant's payload-supporting injector")
        .isNotEmpty();
  }
}
