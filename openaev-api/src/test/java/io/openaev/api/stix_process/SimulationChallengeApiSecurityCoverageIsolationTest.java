package io.openaev.api.stix_process;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Exercise;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "openaev.tenant.active-tables=security_coverages")
@WithMockUser(isAdmin = true)
@DisplayName("SimulationChallengeApi isolation when security_coverages is v2-active")
class SimulationChallengeApiSecurityCoverageIsolationTest extends IntegrationTest {

  private static final Set<Capability> CHALLENGE_CAPABILITIES =
      Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private ExerciseRepository exerciseRepository;

  private String tenantA;
  private String tenantB;

  @AfterEach
  void cleanUp() {
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("challenge validate from tenant B cannot access tenant A challenge")
  void challengeValidateFromTenantBCannotAccessTenantAChallenge() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("sec-cov-challenge-a", CHALLENGE_CAPABILITIES).getId();
    tenantB = tenantHelper.createTenantWithCapabilities("sec-cov-challenge-b", CHALLENGE_CAPABILITIES).getId();

    Exercise exerciseA = createExerciseForTenant(tenantA);
    Challenge challengeA = createChallengeForTenant(tenantA);

    mvc.perform(
            post(
                    "/api/tenants/{tenantId}/player/challenges/{exerciseId}/{challengeId}/validate",
                    tenantB,
                    exerciseA.getId(),
                    challengeA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challenge_value\":\"flag value\"}")
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  private Exercise createExerciseForTenant(String tenantId) {
    return inTenant(tenantId, () -> exerciseRepository.save(ExerciseFixture.createDefaultExercise()));
  }

  private Challenge createChallengeForTenant(String tenantId) {
    return inTenant(tenantId, () -> challengeRepository.save(ChallengeFixture.createDefaultChallenge()));
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    String previousTenant = TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
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

