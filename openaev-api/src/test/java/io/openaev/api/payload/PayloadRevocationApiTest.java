package io.openaev.api.payload;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.payload.dto.PayloadRevocationInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "openaev.enabled-dev-features=CATALOG")
@DisplayName("Payload revocation by a tenant admin")
class PayloadRevocationApiTest extends IntegrationTest {

  private static final String REVOCATION_URI = TENANT_PAYLOAD_URI + "/{payloadId}/revocation";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadRevocationRepository payloadRevocationRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private Payload payloadIn(String tenantId) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setTenant(new Tenant(tenantId));
    return payloadRepository.save(payload);
  }

  private ResultActions revoke(String tenantId, String payloadId) throws Exception {
    return mvc.perform(
        post(REVOCATION_URI, tenantId, payloadId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new PayloadRevocationInput("Deletes user files")))
            .with(csrf()));
  }

  private ResultActions lift(String tenantId, String payloadId) throws Exception {
    return mvc.perform(delete(REVOCATION_URI, tenantId, payloadId).with(csrf()));
  }

  @Test
  @DisplayName("an admin with the revoke right revokes a payload version, then lifts it")
  @WithMockUser(
      withCapabilities = {Capability.REVOKE_THREAT_ARSENALS},
      autoJoinDefaultTenant = true)
  void given_revokeRight_should_revokeThenLift() throws Exception {
    // Arrange
    String payloadId = payloadIn(DEFAULT_TENANT_UUID).getId();

    // Act
    revoke(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isCreated());
    PayloadRevocation revocation =
        payloadRevocationRepository
            .findByTenantIdAndPayloadId(DEFAULT_TENANT_UUID, payloadId)
            .orElseThrow();
    lift(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isNoContent());

    // Assert
    assertThat(revocation.getReason()).isEqualTo("Deletes user files");
    assertThat(revocation.getUser()).isNotNull();
    assertThat(
            payloadRevocationRepository.existsByTenantIdAndPayloadId(
                DEFAULT_TENANT_UUID, payloadId))
        .isFalse();
  }

  @Test
  @DisplayName(
      "a revoked version cannot be revoked again, and a version not revoked cannot be lifted")
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_revocationState_should_refuseTheSameChangeTwice() throws Exception {
    // Arrange
    String revoked = payloadIn(DEFAULT_TENANT_UUID).getId();
    String notRevoked = payloadIn(DEFAULT_TENANT_UUID).getId();
    revoke(DEFAULT_TENANT_UUID, revoked).andExpect(status().isCreated());

    // Act & Assert
    revoke(DEFAULT_TENANT_UUID, revoked).andExpect(status().isBadRequest());
    lift(DEFAULT_TENANT_UUID, notRevoked).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("an admin who only manages the Threat Arsenal cannot revoke")
  @WithMockUser(
      withCapabilities = {Capability.DELETE_THREAT_ARSENALS},
      autoJoinDefaultTenant = true)
  void given_noRevokeRight_should_refuse() throws Exception {
    // Arrange
    String payloadId = payloadIn(DEFAULT_TENANT_UUID).getId();

    // Act & Assert
    revoke(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("a payload of another tenant cannot be revoked")
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_payloadOfAnotherTenant_should_notFindIt() throws Exception {
    // Arrange
    String otherTenant = tenantHelper.createTenantWithCurrentUser("revocation-other").getId();
    String payloadId = payloadIn(otherTenant).getId();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);

    // Act & Assert
    revoke(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isNotFound());
  }
}
