package io.openaev.rest.challenge;

import static io.openaev.rest.challenge.ChallengeApi.CHALLENGE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Challenge;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /challenges/find} returns raw {@code Challenge} entities and force-loads their {@code
 * challenge_documents} inside the scoped transaction (once {@code documents} is v2-active the
 * open-in-view lazy load runs after commit with the tenant scope cleared and fails closed to an
 * empty array). The endpoint loads the association with a single fetch-joined query rather than one
 * lazy-initialization SELECT per challenge, so the documents load does not grow with the number of
 * requested ids while the serialized payload is unchanged.
 *
 * <p>{@code @TestPropertySource} activates {@code documents} for this test only (the test classpath
 * keeps the allowlist empty). The class is NOT {@code @Transactional}: a rolled-back test
 * transaction never commits, so the tenant-local GUC would stay alive through serialization and
 * mask the very failure the payload assertions pin. Seeding therefore goes through an
 * auto-committing {@link JdbcTemplate} and is removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("POST /challenges/find bulk-loads challenge documents in a single scoped query")
class ChallengeFindBulkDocumentsTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<String> seededChallenges = new ArrayList<>();
  private final List<String> seededDocuments = new ArrayList<>();

  @BeforeEach
  void seed() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("challenge-find-bulk").getId();
  }

  @AfterEach
  void cleanup() {
    for (String challengeId : seededChallenges) {
      jdbc.update("DELETE FROM challenges_documents WHERE challenge_id = ?", challengeId);
      jdbc.update("DELETE FROM challenges WHERE challenge_id = ?", challengeId);
    }
    for (String documentId : seededDocuments) {
      jdbc.update("DELETE FROM documents WHERE document_id = ?", documentId);
    }
    seededChallenges.clear();
    seededDocuments.clear();
  }

  @Nested
  @DisplayName("Serialized payload")
  class Payload {

    @Test
    @DisplayName(
        "given several requested challenges, one carrying a document and one carrying none, when"
            + " found then each is serialized with its own documents")
    void given_challengesWithAndWithoutDocuments_should_serializeEachWithItsOwnDocuments()
        throws Exception {
      // -- Arrange --
      String withDocument = seedChallenge("bulk-with-document");
      String documentId = seedDocument("bulk-document");
      linkDocumentToChallenge(withDocument, documentId);
      String withoutDocument = seedChallenge("bulk-without-document");

      // -- Act & Assert --
      // The document-carrying challenge must keep its link (fetch-joined inside the scope) and the
      // document-less one must still be returned with an empty array: the outer join must not drop
      // a challenge that has no in-scope document.
      mvc.perform(
              post(CHALLENGE_URI + "/find")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(idsJson(List.of(withDocument, withoutDocument)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath(
                  "$[?(@.challenge_id=='" + withDocument + "')].challenge_documents[*]",
                  hasItem(documentId)))
          .andExpect(
              jsonPath(
                  "$[?(@.challenge_id=='" + withoutDocument + "')].challenge_documents[*]",
                  empty()));
    }
  }

  @Nested
  @DisplayName("Query count")
  class QueryCount {

    @Test
    @DisplayName(
        "given more requested ids when loaded then the documents are fetch-joined in a single"
            + " query")
    void given_moreRequestedIds_should_fetchJoinDocumentsInASingleQuery() {
      // -- Arrange --
      String single = seedChallenge("bulk-count-single");
      linkDocumentToChallenge(single, seedDocument("bulk-count-single-doc"));
      List<String> many = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        String challengeId = seedChallenge("bulk-count-many-" + i);
        linkDocumentToChallenge(challengeId, seedDocument("bulk-count-many-doc-" + i));
        many.add(challengeId);
      }

      // -- Act & Assert --
      // A single statement loads the challenges with their documents fetch-joined and initialized,
      // for one id and for many; the count does not grow with the number of requested ids (the
      // per-challenge lazy documents SELECT that the enrich path issued is gone).
      long oneId = statementsToFetchDocuments(List.of(single));
      long manyIds = statementsToFetchDocuments(many);
      assertThat(manyIds)
          .as("the documents fetch-join must load in a single query, regardless of the id count")
          .isEqualTo(oneId);
    }

    private long statementsToFetchDocuments(List<String> ids) {
      Statistics stats =
          entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
      stats.setStatisticsEnabled(true);
      return tenantScopedJobRunner.supplyInTenant(
          tenant,
          () -> {
            stats.clear();
            List<Challenge> loaded = challengeRepository.findAllByIdInFetchingDocuments(ids);
            // The documents collection must already be initialized by the fetch-join, so
            // serialization never triggers a per-challenge lazy load.
            loaded.forEach(
                challenge ->
                    assertThat(Hibernate.isInitialized(challenge.getDocuments()))
                        .as("the fetch-join must initialize the documents collection")
                        .isTrue());
            return stats.getPrepareStatementCount();
          });
    }
  }

  private static String idsJson(List<String> ids) {
    return ids.stream().collect(Collectors.joining("\",\"", "[\"", "\"]"));
  }

  private String seedChallenge(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO challenges (challenge_id, challenge_name, tenant_id) VALUES (?, ?, ?)",
        id,
        name + "-" + UUID.randomUUID(),
        tenant);
    seededChallenges.add(id);
    return id;
  }

  private String seedDocument(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'image/png', ?, ?)",
        id,
        name + "-" + UUID.randomUUID(),
        name,
        tenant);
    seededDocuments.add(id);
    return id;
  }

  private void linkDocumentToChallenge(String challengeId, String documentId) {
    jdbc.update(
        "INSERT INTO challenges_documents (challenge_id, document_id) VALUES (?, ?)",
        challengeId,
        documentId);
  }
}
