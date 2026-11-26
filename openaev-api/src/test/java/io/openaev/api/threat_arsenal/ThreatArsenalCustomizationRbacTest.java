package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(withCapabilities = {Capability.ACCESS_THREAT_ARSENALS})
@TestPropertySource(properties = "openaev.enabled-dev-features=CATALOG")
@DisplayName("Customized copies for a user who can only read the Threat Arsenal")
class ThreatArsenalCustomizationRbacTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;

  private InjectorContract action;

  @BeforeEach
  void anAction() {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    action =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
            .persist()
            .get();
    entityManager.flush();
  }

  @Nested
  @DisplayName("Write refused")
  class WriteRefused {

    @Test
    @DisplayName("refuses to customize an action")
    void given_readOnlyUser_should_refuseToCustomize() throws Exception {
      // Act & Assert
      mvc.perform(
              post(
                      TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize",
                      DEFAULT_TENANT_UUID,
                      action.getId())
                  .with(csrf()))
          .andExpect(status().isForbidden());
      assertThat(payloadRepository.findAll()).noneMatch(Payload::isCustomized);
    }

    @Test
    @DisplayName("refuses to update a customized copy")
    void given_readOnlyUser_should_refuseToUpdate() throws Exception {
      // Act & Assert
      mvc.perform(
              post(
                      TENANT_THREAT_ARSENAL_URL + "/{actionId}/customization/update",
                      DEFAULT_TENANT_UUID,
                      action.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"update_payload_id\":\"any\",\"update_choices\":{}}"))
          .andExpect(status().isForbidden());
    }
  }
}
