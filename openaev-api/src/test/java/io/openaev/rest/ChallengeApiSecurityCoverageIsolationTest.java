package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.rest.challenge.form.ChallengeInput;
import io.openaev.rest.challenge.form.FlagInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "openaev.tenant.active-tables=security_coverages")
@WithMockUser(isAdmin = true)
@DisplayName("ChallengeApi tenant isolation when security_coverages is v2-active")
class ChallengeApiSecurityCoverageIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private ExerciseRepository exerciseRepository;

  private String tenantAId;
  private String tenantBId;

  @AfterEach
  void cleanUpTenants() {
    tenantIsolationHelper.deleteCommittedTenants(tenantAId, tenantBId);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("challenge from tenant A is not tryable from tenant B")
  void challengeFromTenantAIsNotTryableFromTenantB() throws Exception {
    Tenant tenantA =
        tenantIsolationHelper.createTenantWithCapabilities(
            "SecCov Tenant A", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
    Tenant tenantB =
        tenantIsolationHelper.createTenantWithCapabilities(
            "SecCov Tenant B", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
    tenantAId = tenantA.getId();
    tenantBId = tenantB.getId();

    String challengeId = createChallengeInTenant(tenantAId, "Security coverage phase5 challenge");

    int responseStatus =
        mvc.perform(
                post("/api/tenants/" + tenantBId + "/challenges/" + challengeId + "/try")
                    .content("{\"challenge_value\":\"secret-flag\"}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus();

    assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  @DisplayName("simulation challenge validate from tenant B cannot access tenant A challenge")
  void simulationChallengeValidateFromTenantBCannotAccessTenantAChallenge() throws Exception {
    Tenant tenantA =
        tenantIsolationHelper.createTenantWithCapabilities(
            "SecCov Validate Tenant A",
            Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
    Tenant tenantB =
        tenantIsolationHelper.createTenantWithCapabilities(
            "SecCov Validate Tenant B",
            Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
    tenantAId = tenantA.getId();
    tenantBId = tenantB.getId();

    Exercise exerciseA =
        inTenant(tenantAId, () -> exerciseRepository.save(ExerciseFixture.createDefaultExercise()));
    Challenge challengeA =
        inTenant(
            tenantAId, () -> challengeRepository.save(ChallengeFixture.createDefaultChallenge()));

    mvc.perform(
            post(
                    "/api/tenants/{tenantId}/player/challenges/{exerciseId}/{challengeId}/validate",
                    tenantBId,
                    exerciseA.getId(),
                    challengeA.getId())
                .content("{\"challenge_value\":\"flag value\"}")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  private ChallengeInput createChallengeInput(String name) {
    FlagInput flag = new FlagInput();
    flag.setType("VALUE");
    flag.setValue("secret-flag");
    return new ChallengeInput(
        name, "category", "content", 100.0, 3, List.of(), List.of(), List.of(flag));
  }

  private String createChallengeInTenant(String tenantId, String name) throws Exception {
    ChallengeInput input = createChallengeInput(name);

    String response =
        mvc.perform(
                post("/api/tenants/" + tenantId + "/challenges")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return JsonPath.read(response, "$.challenge_id");
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
