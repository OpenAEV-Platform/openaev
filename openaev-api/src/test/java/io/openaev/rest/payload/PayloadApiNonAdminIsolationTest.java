package io.openaev.rest.payload;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadInputFixture;
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
 * Non-admin counterpart of {@link PayloadApiTenantIsolationTest}: the injector-contract scoping fix
 * must not depend on RBAC being bypassed by {@code isAdmin = true}. Runs as a non-admin holding
 * only {@code MANAGE_PAYLOADS} in the tenant.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = false)
@DisplayName("PayloadApi scopes the payload-supporting injector lookup for a non-admin caller")
class PayloadApiNonAdminIsolationTest extends IntegrationTest {

  private static final String CREATE = "/api/tenants/{tenantId}/payloads";
  private static final Set<Capability> MANAGE_PAYLOADS = Set.of(Capability.MANAGE_PAYLOADS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;

  private String tenantA;

  @BeforeEach
  void seedTenantWithPayloadSupportingInjector() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("payload-nonadmin-iso-a", MANAGE_PAYLOADS)
            .getId();
  }

  @Test
  @DisplayName(
      "a non-admin creating a command-line payload under tenant A's path links the contract to"
          + " A's injector")
  void nonAdminCreatingPayloadUnderTenantPathLinksContractToTenantInjector() throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());

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

    assertNotNull(response, "the created payload response must not be null");
    String payloadId = JsonPath.read(response, "$.payload_id");
    Payload payload = payloadRepository.findById(payloadId).orElse(null);
    assertNotNull(payload, "the payload itself must be saved");

    InjectorContract contract =
        injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
    assertNotNull(
        contract,
        "the injector contract must be created so the payload shows up in the threat arsenal");
    assertThat(contract.getInjectors())
        .as("the contract must be linked to the tenant's payload-supporting injector")
        .isNotEmpty();
  }
}
