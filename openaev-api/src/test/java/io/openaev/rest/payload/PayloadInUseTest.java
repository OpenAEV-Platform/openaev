package io.openaev.rest.payload;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A payload that injects run")
class PayloadInUseTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private PayloadRepository payloadRepository;

  private Inject inject;

  @BeforeEach
  void setup() {
    scenarioComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    scenarioComposer
        .forScenario(ScenarioFixture.getScenario())
        .withInject(
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withInjectorContract(
                    injectorContractComposer
                        .forInjectorContract(
                            InjectorContractFixture.createDefaultInjectorContract())
                        .withPayload(
                            payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))))
        .persist();
    inject = injectComposer.generatedItems.getFirst();
    entityManager.flush();
  }

  @Test
  @DisplayName("is not deleted, as a payload or as a threat arsenal action")
  void given_payloadRunByInjects_should_refuseItsDelete() throws Exception {
    // Arrange
    String payloadId = inject.getInjectorContract().orElseThrow().getPayload().getId();
    String actionId = inject.getInjectorContract().orElseThrow().getId();

    // Act & Assert
    mvc.perform(delete(TENANT_PAYLOAD_URI + "/{id}", DEFAULT_TENANT_UUID, payloadId).with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            delete(TENANT_THREAT_ARSENAL_URL + "/{id}", DEFAULT_TENANT_UUID, actionId).with(csrf()))
        .andExpect(status().isBadRequest());
    assertThat(payloadRepository.findById(payloadId)).isPresent();
  }
}
