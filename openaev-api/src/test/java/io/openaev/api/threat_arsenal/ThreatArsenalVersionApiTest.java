package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.helpers.PayloadVersionTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Threat Arsenal payload versions (F22, F30)")
class ThreatArsenalVersionApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ContentRevocationRepository contentRevocationRepository;
  @Autowired private PayloadVersionTestHelper payloadVersionTestHelper;

  // The highest version is the latest: the pointer is computed, never set by a test.
  private InjectorContract contractOf(String externalId, String version, boolean latest) {
    InjectorContract contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
            .persist()
            .get();
    payloadVersionTestHelper.asManagedVersion(contract.getPayload(), externalId, version);
    return contract;
  }

  private Inject atomicTestingOn(InjectorContract contract) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(contract);
    return injectComposer.forInject(inject).persist().get();
  }

  private void revoke(Payload payload) {
    ContentRevocation revocation = new ContentRevocation();
    revocation.setType(ContentType.PAYLOAD);
    revocation.setPayload(payload);
    revocation.setReason("Crashes our domain controllers");
    revocation.setTenant(payload.getTenant());
    contentRevocationRepository.save(revocation);
  }

  private void flush() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("Lists every version, with the current one flagged")
  @WithMockUser(isAdmin = true)
  void given_twoVersions_should_listBoth() throws Exception {
    // Arrange
    String externalId = UUID.randomUUID().toString();
    InjectorContract v1 = contractOf(externalId, "1.0.0", false);
    contractOf(externalId, "2.0.0", true);
    flush();

    // Act & Assert
    mvc.perform(get(THREAT_ARSENAL_URL + "/" + v1.getId() + "/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.payload_version=='1.0.0')].payload_current").value(true))
        .andExpect(jsonPath("$[?(@.payload_version=='2.0.0')].payload_is_latest").value(true));
  }

  @Test
  @DisplayName("Moves an atomic testing to another version")
  @WithMockUser(isAdmin = true)
  void given_atomicTestingOnV1_should_moveItToV2() throws Exception {
    // Arrange
    String externalId = UUID.randomUUID().toString();
    InjectorContract v1 = contractOf(externalId, "1.0.0", false);
    InjectorContract v2 = contractOf(externalId, "2.0.0", true);
    Inject inject = atomicTestingOn(v1);
    flush();

    // Act
    mvc.perform(
            post(THREAT_ARSENAL_URL + "/" + v1.getId() + "/change-version")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    asJsonString(
                        Map.of(
                            "target_payload_id",
                            v2.getPayload().getId(),
                            "inject_ids",
                            List.of(inject.getId()))))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moved_ids[0]").value(inject.getId()))
        .andExpect(jsonPath("$.refused.length()").value(0));

    // Assert
    flush();
    assertEquals(
        v2.getId(),
        injectRepository
            .findById(inject.getId())
            .orElseThrow()
            .getInjectorContract()
            .orElseThrow()
            .getId());
  }

  @Test
  @DisplayName("Refuses a revoked target version")
  @WithMockUser(isAdmin = true)
  void given_revokedTarget_should_refuseChange() throws Exception {
    // Arrange
    String externalId = UUID.randomUUID().toString();
    InjectorContract v1 = contractOf(externalId, "1.0.0", false);
    InjectorContract v2 = contractOf(externalId, "2.0.0", true);
    Inject inject = atomicTestingOn(v1);
    revoke(v2.getPayload());
    flush();

    // Act & Assert
    mvc.perform(
            post(THREAT_ARSENAL_URL + "/" + v1.getId() + "/change-version")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    asJsonString(
                        Map.of(
                            "target_payload_id",
                            v2.getPayload().getId(),
                            "inject_ids",
                            List.of(inject.getId()))))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Refuses to duplicate a revoked payload")
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_revokedPayload_should_refuseDuplicate() throws Exception {
    // Arrange
    InjectorContract v1 = contractOf(UUID.randomUUID().toString(), "1.0.0", true);
    revoke(v1.getPayload());
    flush();

    // Act & Assert
    mvc.perform(post(THREAT_ARSENAL_URL + "/" + v1.getId() + "/duplicate").with(csrf()))
        .andExpect(status().isBadRequest());
  }
}
