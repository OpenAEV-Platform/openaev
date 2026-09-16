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
import io.openaev.database.model.Capability;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-admin counterpart of {@link ThreatArsenalApiTenantIsolationTest}: the injector-contract
 * scoping fix must not depend on RBAC being bypassed by {@code isAdmin = true}. Runs as a non-admin
 * holding only {@code MANAGE_THREAT_ARSENALS} in the tenant.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = false)
@DisplayName(
    "ThreatArsenalApi scopes the payload-supporting injector lookup for a non-admin caller")
class ThreatArsenalApiNonAdminIsolationTest extends IntegrationTest {

  private static final String CREATE = "/api/tenants/{tenantId}/threat_arsenals";
  private static final Set<Capability> MANAGE_THREAT_ARSENAL =
      Set.of(Capability.MANAGE_THREAT_ARSENALS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;

  private String tenantA;

  @BeforeEach
  void seedTenantWithPayloadSupportingInjector() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("ta-nonadmin-iso-a", MANAGE_THREAT_ARSENAL)
            .getId();
  }

  @Test
  @DisplayName(
      "a non-admin creating a command-line action under tenant A's path links the contract to A's"
          + " injector")
  void nonAdminCreatingActionUnderTenantPathLinksContractToTenantInjector() throws Exception {
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
