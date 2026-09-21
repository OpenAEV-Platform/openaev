package io.openaev.rest.challenge;

import static io.openaev.rest.challenge.ChallengeApi.CHALLENGE_URI;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With {@code documents} on v2 tenant isolation (which the default test properties do NOT
 * exercise), the challenge read endpoints return the raw {@code Challenge} entity and serialize its
 * {@code challenge_documents} lazy {@code @ManyToMany} open-in-view, AFTER the controller
 * transaction has committed. {@code TenantScopeTransactionAspect} only sets {@code
 * app.current_tenants} for the duration of that transaction (it is {@code set_config(..., true)},
 * transaction-local), so the post-commit lazy load runs unscoped and fails closed to an empty
 * array, the same silent open-in-view regression that hit {@code security_platform_collectors} in
 * production (#7025/#7026). {@code ChallengeService} force-initializes the association inside the
 * scoped transaction to prevent it.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local GUC would stay alive through serialization and MASK the very failure
 * this pins. Seeding therefore goes through an auto-committing {@link JdbcTemplate} and is removed
 * on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Challenge read endpoints serialize their documents with documents v2-activated")
class ChallengeDocumentsSerializationTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<String> seededChallenges = new ArrayList<>();
  private final List<String> seededDocuments = new ArrayList<>();

  @BeforeEach
  void seed() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("challenge-doc-sink").getId();
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

  @Test
  @DisplayName(
      "given a challenge carrying a document when the challenges are listed then the document link"
          + " is serialized")
  void
      given_a_challenge_carrying_a_document_when_the_challenges_are_listed_then_the_document_link_is_serialized()
          throws Exception {
    // -- Arrange --
    String challengeId = seedChallenge("sink-with-document");
    String documentId = seedDocument("sink-document");
    linkDocumentToChallenge(challengeId, documentId);

    // -- Act & Assert --
    // Without the in-scope force-initialize the lazy challenge_documents serializes as [] here: the
    // post-commit open-in-view load runs with app.current_tenants cleared and fails closed.
    mvc.perform(get(CHALLENGE_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[?(@.challenge_id=='" + challengeId + "')].challenge_documents[*]",
                hasItem(documentId)));
  }

  @Test
  @DisplayName(
      "given a challenge with no document when the challenges are listed then its documents are"
          + " genuinely empty")
  void
      given_a_challenge_with_no_document_when_the_challenges_are_listed_then_its_documents_are_empty()
          throws Exception {
    // -- Arrange --
    String challengeId = seedChallenge("sink-without-document");

    // -- Act & Assert --
    // Empty here proves the assertion above tracks the real link and is not always non-empty.
    mvc.perform(get(CHALLENGE_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[?(@.challenge_id=='" + challengeId + "')].challenge_documents[*]", empty()));
  }

  @Test
  @DisplayName(
      "given a challenge carrying a document when found by id then the document link is serialized")
  void given_a_challenge_carrying_a_document_when_found_by_id_then_the_document_link_is_serialized()
      throws Exception {
    // -- Arrange --
    String challengeId = seedChallenge("find-with-document");
    String documentId = seedDocument("find-document");
    linkDocumentToChallenge(challengeId, documentId);

    // -- Act & Assert --
    // POST /challenges/find returns raw Challenge entities too: without the in-scope
    // force-initialize
    // its lazy challenge_documents serializes as [] once documents is v2-active.
    mvc.perform(
            post(CHALLENGE_URI + "/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + challengeId + "\"]")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[?(@.challenge_id=='" + challengeId + "')].challenge_documents[*]",
                hasItem(documentId)));
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
