package io.openaev.config;

import static io.openaev.config.LazyAfterCommitProbeTest.PROBE_CHALLENGE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.LazyAfterCommitRecorder.Incident;
import io.openaev.config.LazyAfterCommitRecorder.Verdict;
import io.openaev.database.model.Challenge;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The same endpoint as {@link LazyAfterCommitProbeTest}, from a {@code @Transactional} test, which
 * is how most of the suite is written. The test transaction outlives the request, so the scope is
 * still set while Jackson serialises and the response carries the tags: the test cannot see the
 * defect. The probe must still report it, by replaying the read under the empty scope production
 * runs it with.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges,tags")
@Import({LazyAfterCommitProbeTestConfig.class, LazyAfterCommitProbeTest.ProbeEndpointConfig.class})
@WithMockUser(isAdmin = true)
@DisplayName("Lazy-after-commit probe under a test transaction")
class LazyAfterCommitProbeMaskedTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenant;
  private String challengeId;

  @BeforeEach
  void seedChallengeWithTwoTags() throws Exception {
    tenant = tenantHelper.createTenantWithCurrentUser("lazy-after-commit-masked").getId();
    challengeId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO challenges (challenge_id, challenge_name, challenge_category,"
                + " challenge_content, challenge_score, challenge_max_attempts, tenant_id)"
                + " VALUES (?1, ?2, 'cat', 'content', 10, 3, ?3)")
        .setParameter(1, challengeId)
        .setParameter(2, "lazy-after-commit-masked-" + challengeId)
        .setParameter(3, tenant)
        .executeUpdate();
    for (int i = 0; i < 2; i++) {
      String tagId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery("INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?1, ?2, ?3)")
          .setParameter(1, tagId)
          .setParameter(2, "lazy-after-commit-masked-" + tagId)
          .setParameter(3, tenant)
          .executeUpdate();
      entityManager
          .createNativeQuery("INSERT INTO challenges_tags (challenge_id, tag_id) VALUES (?1, ?2)")
          .setParameter(1, challengeId)
          .setParameter(2, tagId)
          .executeUpdate();
    }
    LazyAfterCommitRecorder.clear();
  }

  @Nested
  @DisplayName("A lazy collection serialised while the test transaction is still open")
  class MaskedByTheTestTransaction {

    @Test
    @DisplayName("given linked tags, when the entity is returned, then the test sees the tags")
    void given_linkedTags_should_serializeTagsInsideTestTransaction() throws Exception {
      // -- Act & Assert --
      // What the suite sees, and why it never caught the defect.
      mvc.perform(get(PROBE_CHALLENGE_URI, tenant, challengeId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.challenge_tags", hasSize(2)));
    }

    @Test
    @DisplayName("given linked tags, when the entity is returned, then the probe replays the loss")
    void given_linkedTags_should_recordConfirmedReplayedIncident() throws Exception {
      // -- Act --
      mvc.perform(get(PROBE_CHALLENGE_URI, tenant, challengeId)).andExpect(status().isOk());

      // -- Assert --
      assertThat(LazyAfterCommitRecorder.incidents()).hasSize(1);
      Incident incident = LazyAfterCommitRecorder.incidents().getFirst();
      assertThat(incident.endpoint()).isEqualTo("GET " + PROBE_CHALLENGE_URI);
      assertThat(incident.association()).isEqualTo("Challenge.tags");
      assertThat(incident.transactionActive()).isTrue();
      assertThat(incident.scopeAtRead()).isEqualTo(tenant);
      assertThat(incident.rowsUnderRequestScope()).isEqualTo(2);
      assertThat(incident.rowsUnderEmptyScope()).isZero();
      assertThat(incident.verdict()).isEqualTo(Verdict.CONFIRMED_REPLAYED);
    }

    @Test
    @DisplayName(
        "given the test already holds the entity, when it is returned, then the loss is reported")
    void given_entityAlreadyInTestContext_should_recordConfirmedReplayedIncident()
        throws Exception {
      // -- Arrange --
      // The test's persistence context is the request's: without isolation the endpoint would
      // return this very instance, its tags already in memory, and issue no statement at all.
      Challenge held = entityManager.find(Challenge.class, challengeId);
      Hibernate.initialize(held.getTags());
      assertThat(held.getTags()).hasSize(2);

      // -- Act --
      mvc.perform(get(PROBE_CHALLENGE_URI, tenant, challengeId)).andExpect(status().isOk());

      // -- Assert --
      assertThat(LazyAfterCommitRecorder.incidents())
          .extracting(Incident::association, Incident::verdict)
          .containsExactly(tuple("Challenge.tags", Verdict.CONFIRMED_REPLAYED));
    }
  }
}
