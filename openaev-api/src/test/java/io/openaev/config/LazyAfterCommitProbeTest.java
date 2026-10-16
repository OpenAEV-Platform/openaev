package io.openaev.config;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.aop.AccessControl;
import io.openaev.config.LazyAfterCommitRecorder.Incident;
import io.openaev.config.LazyAfterCommitRecorder.Phase;
import io.openaev.config.LazyAfterCommitRecorder.Verdict;
import io.openaev.config.LazySerializationRecorder.Observation;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Proves the lazy-after-commit probe fires before any of its measurements is trusted.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a test transaction never commits, so the
 * request's scope would still be set while Jackson serialises and the defect would not happen. Rows
 * are seeded through an auto-committing {@link JdbcTemplate} and removed on teardown. {@link
 * LazyAfterCommitProbeMaskedTest} covers the transactional case, which is what most of the suite
 * runs.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges,tags")
@Import({LazyAfterCommitProbeTestConfig.class, LazyAfterCommitProbeTest.ProbeEndpointConfig.class})
@WithMockUser(isAdmin = true)
@DisplayName("Lazy-after-commit probe on committed data")
class LazyAfterCommitProbeTest extends IntegrationTest {

  static final String PROBE_CHALLENGE_URI =
      TENANT_PREFIX + "/lazy-after-commit-probe/challenges/{challengeId}";
  static final String PROBE_INITIALIZED_CHALLENGE_URI = PROBE_CHALLENGE_URI + "/initialized";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private String challengeId;
  private final List<String> tagIds = new ArrayList<>();
  private final List<String[]> seededRows = new ArrayList<>();

  @BeforeEach
  void seedChallengeWithTwoTags() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("lazy-after-commit-probe").getId();
    challengeId = seedChallenge();
    for (int i = 0; i < 2; i++) {
      String tagId = seedTag();
      tagIds.add(tagId);
      jdbc.update(
          "INSERT INTO challenges_tags (challenge_id, tag_id) VALUES (?, ?)", challengeId, tagId);
      seededRows.add(new String[] {"challenges_tags", "tag_id", tagId});
    }
    LazyAfterCommitRecorder.clear();
    LazySerializationRecorder.clear();
  }

  @AfterEach
  void cleanup() {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tagIds.clear();
    tenantHelper.deleteCommittedTenants(tenant);
    LazyAfterCommitRecorder.clear();
    LazySerializationRecorder.clear();
  }

  @Nested
  @DisplayName("A lazy collection serialised after the commit")
  class SerialisedAfterCommit {

    @Test
    @DisplayName("given linked tags, when the entity is returned, then the tags come back empty")
    void given_linkedTags_should_serializeEmptyTagsAfterCommit() throws Exception {
      // -- Act & Assert --
      // The defect itself, observed: two links exist and the response says there are none.
      mvc.perform(get(PROBE_CHALLENGE_URI, tenant, challengeId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.challenge_tags", empty()));
    }

    @Test
    @DisplayName("given linked tags, when the entity is returned, then the probe reports the loss")
    void given_linkedTags_should_recordConfirmedObservedIncident() throws Exception {
      // -- Act --
      mvc.perform(get(PROBE_CHALLENGE_URI, tenant, challengeId)).andExpect(status().isOk());

      // -- Assert --
      assertThat(LazyAfterCommitRecorder.incidents()).hasSize(1);
      Incident incident = LazyAfterCommitRecorder.incidents().getFirst();
      assertThat(incident.endpoint()).isEqualTo("GET " + PROBE_CHALLENGE_URI);
      assertThat(incident.handler()).isEqualTo("ProbeChallengeController.challenge");
      assertThat(incident.phase()).isEqualTo(Phase.SERIALIZATION);
      assertThat(incident.association()).isEqualTo("Challenge.tags");
      assertThat(incident.ownerId()).isEqualTo(challengeId);
      assertThat(incident.tables()).contains("tags");
      assertThat(incident.transactionActive()).isFalse();
      assertThat(incident.scopeAtRead()).isEmpty();
      assertThat(incident.requestScope()).isEqualTo(tenant);
      assertThat(incident.rowsUnderRequestScope()).isEqualTo(2);
      assertThat(incident.rowsUnderEmptyScope()).isZero();
      assertThat(incident.verdict()).isEqualTo(Verdict.CONFIRMED_OBSERVED);
    }

    @Test
    @DisplayName("given linked tags, when the entity is returned, then the tags were still lazy")
    void given_linkedTags_should_observeTagsLazyWhenWritten() throws Exception {
      // -- Act --
      mvc.perform(get(PROBE_CHALLENGE_URI, tenant, challengeId)).andExpect(status().isOk());

      // -- Assert --
      assertThat(LazySerializationRecorder.counts())
          .containsKey(
              new Observation(
                  "GET " + PROBE_CHALLENGE_URI,
                  "ProbeChallengeController.challenge",
                  "Challenge.tags",
                  false))
          .doesNotContainKey(
              new Observation(
                  "GET " + PROBE_CHALLENGE_URI,
                  "ProbeChallengeController.challenge",
                  "Challenge.tags",
                  true));
    }
  }

  @Nested
  @DisplayName("A lazy collection initialised inside the scope")
  class InitialisedInsideScope {

    @Test
    @DisplayName("given linked tags initialised in the transaction, then the tags are serialised")
    void given_tagsInitializedInScope_should_serializeTags() throws Exception {
      // -- Act & Assert --
      mvc.perform(get(PROBE_INITIALIZED_CHALLENGE_URI, tenant, challengeId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.challenge_tags", containsInAnyOrder(tagIds.toArray())));
    }

    @Test
    @DisplayName("given linked tags initialised in the transaction, then the probe stays silent")
    void given_tagsInitializedInScope_should_recordNothing() throws Exception {
      // -- Act --
      mvc.perform(get(PROBE_INITIALIZED_CHALLENGE_URI, tenant, challengeId))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(LazyAfterCommitRecorder.incidents()).isEmpty();
    }

    @Test
    @DisplayName(
        "given linked tags initialised in the transaction, then they were initialised when written")
    void given_tagsInitializedInScope_should_observeTagsInitializedWhenWritten() throws Exception {
      // -- Act --
      mvc.perform(get(PROBE_INITIALIZED_CHALLENGE_URI, tenant, challengeId))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(LazySerializationRecorder.counts())
          .containsKey(
              new Observation(
                  "GET " + PROBE_INITIALIZED_CHALLENGE_URI,
                  "ProbeChallengeController.challengeWithTags",
                  "Challenge.tags",
                  true))
          .doesNotContainKey(
              new Observation(
                  "GET " + PROBE_INITIALIZED_CHALLENGE_URI,
                  "ProbeChallengeController.challengeWithTags",
                  "Challenge.tags",
                  false));
    }
  }

  @Nested
  @DisplayName("A read the test itself makes after the response")
  class ReadByTheTestAfterTheResponse {

    @Test
    @DisplayName(
        "given a response already written, when the test reads a gated table, then the probe stays silent")
    void given_responseWritten_should_notRecordTheTestsOwnRead() throws Exception {
      // -- Arrange --
      // A finished request can stay bound to the test thread, with its matched handler, so a read
      // the test makes afterwards looks like one the request made unless the probe checks that a
      // dispatch is running.
      HttpServletRequest finished =
          mvc.perform(get(PROBE_INITIALIZED_CHALLENGE_URI, tenant, challengeId))
              .andExpect(status().isOk())
              .andReturn()
              .getRequest();
      RequestAttributes previous = RequestContextHolder.getRequestAttributes();
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(finished));

      // -- Act --
      try {
        jdbc.queryForList(
            "SELECT tag_id FROM tags WHERE can_access_tenant(cast(tenant_id as text))",
            String.class);
      } finally {
        RequestContextHolder.setRequestAttributes(previous);
      }

      // -- Assert --
      assertThat(LazyAfterCommitRecorder.incidents()).isEmpty();
    }
  }

  private String seedChallenge() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO challenges (challenge_id, challenge_name, challenge_category,"
            + " challenge_content, challenge_score, challenge_max_attempts, tenant_id)"
            + " VALUES (?, ?, 'cat', 'content', 10, 3, ?)",
        id,
        "lazy-after-commit-probe-" + id,
        tenant);
    seededRows.add(new String[] {"challenges", "challenge_id", id});
    return id;
  }

  private String seedTag() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "lazy-after-commit-probe-" + id,
        tenant);
    seededRows.add(new String[] {"tags", "tag_id", id});
    return id;
  }

  /**
   * A test-only endpoint whose response is known to carry the defect the lazy-after-commit probe
   * looks for, so the probe can be seen to fire on something real before any measurement is
   * trusted.
   *
   * <p>It reads a challenge inside a scoped transaction and returns the entity, whose lazy {@code
   * challenge_tags} Jackson then initialises after the commit. Its sibling initialises the same
   * collection inside the transaction, which is the attenuation the probe must stay silent on.
   */
  @TestConfiguration
  static class ProbeEndpointConfig {

    @Bean
    ProbeChallengeReader probeChallengeReader(ChallengeRepository challengeRepository) {
      return new ProbeChallengeReader(challengeRepository);
    }

    /** Reads a challenge under the request's scope. */
    public static class ProbeChallengeReader {

      private final ChallengeRepository challengeRepository;

      ProbeChallengeReader(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
      }

      @Transactional(readOnly = true)
      public Challenge read(TxCtx ctx, String challengeId) {
        return challengeRepository.findById(challengeId).orElseThrow();
      }

      @Transactional(readOnly = true)
      public Challenge readWithTags(TxCtx ctx, String challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow();
        Hibernate.initialize(challenge.getTags());
        return challenge;
      }
    }

    /**
     * Returns the raw entity, the shape whose serialisation runs after the commit. Registered as a
     * member of this configuration, which is why it has no bean method of its own.
     */
    @RestController
    public static class ProbeChallengeController {

      private final ProbeChallengeReader reader;

      ProbeChallengeController(ProbeChallengeReader reader) {
        this.reader = reader;
      }

      @GetMapping(PROBE_CHALLENGE_URI)
      @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CHALLENGE)
      @Transactional(readOnly = true)
      public Challenge challenge(TxCtx ctx, @PathVariable String challengeId) {
        return reader.read(ctx, challengeId);
      }

      @GetMapping(PROBE_INITIALIZED_CHALLENGE_URI)
      @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CHALLENGE)
      @Transactional(readOnly = true)
      public Challenge challengeWithTags(TxCtx ctx, @PathVariable String challengeId) {
        return reader.readWithTags(ctx, challengeId);
      }
    }
  }
}
