package io.openaev.rest.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Certifies the eight native queries of {@link DocumentRepository} against a real PostgreSQL with
 * {@code documents} v2-active. {@link io.openaev.config.TenantStatementInspector} rewrites their
 * SQL at go-live, and two things only a live database can prove: the rewrite is valid SQL
 * PostgreSQL accepts (the {@code GROUP BY d.document_id} projecting {@code d.*} stays legal because
 * documents is kept a base table, #7843), and the read is scoped to the caller (its rows and
 * aggregates, none of another tenant's). The API test profile ships an empty {@code active-tables},
 * so an {@code IntegrationTest} never exercises the rewriter for documents unless it arms the table
 * itself, which this class does. See #7904.
 *
 * <p>Not {@code @Transactional}: the scoped-transaction primitive opens its own transactions and
 * refuses to run inside an active one, so seeding goes through an auto-committing {@link
 * JdbcTemplate}, which also bypasses the inspector and therefore sees every row whatever the scope.
 * Two freshly created tenants keep the assertions exact on a shared database; everything seeded is
 * removed in teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@DisplayName("Document native queries are scoped and valid once documents is v2-active")
class DocumentNativeQueryTenantScopeTest extends IntegrationTest {

  @Autowired private DocumentRepository documentRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @PersistenceContext private EntityManager em;

  private JdbcTemplate jdbc;

  private String tenantA;
  private String tenantB;
  private String docA1;
  private String docA2;
  private String docB1;
  private String tagA;
  private String exerciseA;
  private String scenarioA;
  private String channelA;
  private String channelB;
  private String securityPlatformA;
  private String securityPlatformB;
  private String challengeA;
  private String payloadA;

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("doc-native-a-" + UUID.randomUUID());
    tenantB = seedTenant("doc-native-b-" + UUID.randomUUID());

    // Tenant A: two documents, the first wired to one of every parent the queries read.
    docA1 = seedDocument(tenantA);
    docA2 = seedDocument(tenantA);
    tagA = seedTag(tenantA);
    link("documents_tags", "document_id", docA1, "tag_id", tagA);
    exerciseA = seedExercise(tenantA);
    link("exercises_documents", "exercise_id", exerciseA, "document_id", docA1);
    scenarioA = seedScenario(tenantA);
    link("scenarios_documents", "scenario_id", scenarioA, "document_id", docA1);
    channelA = seedChannelWithLogo(tenantA, docA1);
    securityPlatformA = seedSecurityPlatformWithLogo(tenantA, docA1);
    challengeA = seedChallenge(tenantA);
    link("challenges_documents", "challenge_id", challengeA, "document_id", docA1);
    payloadA = seedPayloadWithFile(tenantA, docA1);
    // Article branch of the relations queries: one on the scenario (docA1), one on the exercise
    // (docA2), so both findAllDistinctBy* return a real row rather than only proving they execute.
    seedArticleWithDocument(channelA, scenarioA, null, docA1);
    seedArticleWithDocument(channelA, null, exerciseA, docA2);

    // Tenant B: one document with its own channel and security platform, so a cross-tenant parent
    // id exists to prove the documents scope, not the parent filter, is what excludes it.
    docB1 = seedDocument(tenantB);
    channelB = seedChannelWithLogo(tenantB, docB1);
    securityPlatformB = seedSecurityPlatformWithLogo(tenantB, docB1);
  }

  @AfterEach
  void cleanup() {
    for (String documentId : List.of(docA1, docA2, docB1)) {
      jdbc.update("DELETE FROM articles_documents WHERE document_id = ?", documentId);
      jdbc.update("DELETE FROM documents_tags WHERE document_id = ?", documentId);
      jdbc.update("DELETE FROM exercises_documents WHERE document_id = ?", documentId);
      jdbc.update("DELETE FROM scenarios_documents WHERE document_id = ?", documentId);
      jdbc.update("DELETE FROM challenges_documents WHERE document_id = ?", documentId);
    }
    for (String tenantId : List.of(tenantA, tenantB)) {
      jdbc.update("DELETE FROM articles WHERE article_channel IN (?, ?)", channelA, channelB);
      jdbc.update("DELETE FROM channels WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM payloads WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tags WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM scenarios WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM challenges WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM documents WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("rawAllDocuments lists only the caller's documents with their aggregates")
  class RawAllDocuments {

    @Test
    @DisplayName("the list is exactly the caller's documents, never another tenant's")
    void given_twoTenants_should_returnOnlyCallersDocuments() {
      // rawAllDocuments carries a SpEL tenant_id predicate resolved from the v1 TenantContext, so
      // both the ambient tenant (for the SpEL) and the v2 scope (for the inspector) are set to A.
      List<RawDocument> documents = rawAllDocumentsAs(tenantA);

      List<String> ids = documents.stream().map(RawDocument::getDocument_id).toList();
      assertTrue(ids.contains(docA1), "A must see its own document docA1: " + ids);
      assertTrue(ids.contains(docA2), "A must see its own document docA2: " + ids);
      assertFalse(ids.contains(docB1), "A must not see B's document: " + ids);
      assertEquals(2, ids.size(), "A seeded exactly two documents: " + ids);
    }

    @Test
    @DisplayName("the tag, exercise and scenario aggregates carry the caller's related ids")
    void given_relatedRows_should_aggregateThemOnTheDocument() {
      RawDocument docA1Row =
          rawAllDocumentsAs(tenantA).stream()
              .filter(d -> d.getDocument_id().equals(docA1))
              .findFirst()
              .orElseThrow();
      assertTrue(docA1Row.getDocument_tags().contains(tagA), "document_tags must carry A's tag");
      assertTrue(
          docA1Row.getDocument_exercises().contains(exerciseA),
          "document_exercises must carry A's exercise");
      assertTrue(
          docA1Row.getDocument_scenarios().contains(scenarioA),
          "document_scenarios must carry A's scenario");
    }
  }

  @Nested
  @DisplayName("the parent-scoped relations queries return the caller's documents and no other's")
  class ParentScopedRelations {

    @Test
    @DisplayName("by channel id: the caller sees the document behind its own channel logo")
    void given_ownChannel_should_returnItsDocument() {
      assertTrue(
          idsOf(scoped(tenantA, () -> documentRepository.rawAllDocumentsByChannelId(channelA)))
              .contains(docA1),
          "the channel's own logo document must be listed");
    }

    @Test
    @DisplayName("by channel id: a cross-tenant channel returns nothing under the caller's scope")
    void given_anotherTenantsChannel_should_returnNothing() {
      // channelB does resolve to docB1 through the join, so the empty result is the documents scope
      // at work, not a missing row. The next test proves the row is reachable under B's scope.
      assertTrue(
          scoped(tenantA, () -> documentRepository.rawAllDocumentsByChannelId(channelB)).isEmpty(),
          "B's channel document must not leak into A's scope");
    }

    @Test
    @DisplayName("by channel id: the same cross-tenant query returns the row under its own scope")
    void given_anotherTenantsChannel_should_returnTheRowUnderThatTenantsScope() {
      // The red half: without the documents scope excluding it, the previous test's empty result
      // would be empty for any scope and would prove nothing. Under B's scope the row is there.
      assertTrue(
          idsOf(scoped(tenantB, () -> documentRepository.rawAllDocumentsByChannelId(channelB)))
              .contains(docB1),
          "B's own scope must still see B's channel document");
    }

    @Test
    @DisplayName("by security platform id: own returns the document, cross-tenant returns nothing")
    void given_securityPlatform_should_scopeToCaller() {
      assertTrue(
          idsOf(
                  scoped(
                      tenantA,
                      () ->
                          documentRepository.rawAllDocumentsBySecurityPlatformId(
                              securityPlatformA)))
              .contains(docA1),
          "the security platform's own logo document must be listed");
      assertTrue(
          scoped(
                  tenantA,
                  () -> documentRepository.rawAllDocumentsBySecurityPlatformId(securityPlatformB))
              .isEmpty(),
          "B's security platform document must not leak into A's scope");
    }

    @Test
    @DisplayName("by challenge id: the caller sees the challenge's document")
    void given_ownChallenge_should_returnItsDocument() {
      assertTrue(
          idsOf(scoped(tenantA, () -> documentRepository.rawAllDocumentsByChallengeId(challengeA)))
              .contains(docA1),
          "the challenge's own document must be listed");
    }

    @Test
    @DisplayName("by payload id: the caller sees the payload's file document")
    void given_ownPayload_should_returnItsDocument() {
      assertTrue(
          idsOf(scoped(tenantA, () -> documentRepository.rawAllDocumentsByPayloadId(payloadA)))
              .contains(docA1),
          "the payload's file-drop document must be listed");
    }
  }

  @Nested
  @DisplayName("the distinct relations queries execute on PostgreSQL and stay scoped")
  class DistinctRelations {

    @Test
    @DisplayName("by scenario id: the article-linked document is returned to the caller only")
    void given_scenario_should_returnLinkedDocumentScopedToCaller() {
      List<String> ids =
          scoped(tenantA, () -> documentRepository.findAllDistinctByScenarioId(scenarioA)).stream()
              .map(io.openaev.database.model.Document::getId)
              .toList();
      assertTrue(ids.contains(docA1), "the scenario's article document must be returned: " + ids);
      assertFalse(ids.contains(docB1), "no other tenant's document may appear: " + ids);
    }

    @Test
    @DisplayName("by simulation id: the article-linked document is returned to the caller only")
    void given_simulation_should_returnLinkedDocumentScopedToCaller() {
      List<String> ids =
          scoped(tenantA, () -> documentRepository.findAllDistinctBySimulationId(exerciseA))
              .stream()
              .map(io.openaev.database.model.Document::getId)
              .toList();
      assertTrue(ids.contains(docA2), "the exercise's article document must be returned: " + ids);
      assertFalse(ids.contains(docB1), "no other tenant's document may appear: " + ids);
    }

    @Test
    @DisplayName("on injects by scenario id: the payload/injector-contract shape executes scoped")
    void given_scenario_should_executeOnInjectsQueryWithoutError() {
      // No inject/injector-contract chain is seeded, so the result is empty; the value certified
      // here is that the rewritten SQL (documents narrowed inside its LEFT JOIN chain) executes on
      // PostgreSQL under a scope rather than being refused or producing invalid SQL at go-live.
      assertDoesNotThrow(
          () ->
              scoped(
                  tenantA,
                  () -> documentRepository.findAllDistinctOnInjectsByScenarioId(scenarioA)),
          "the on-injects relations query must execute once documents is active");
    }
  }

  @Test
  @DisplayName("non-vacuity: an unscoped native read of documents returns nothing (fail-closed)")
  void given_noScope_should_readNoDocuments() {
    // The same active table read with no scope set returns zero rows: the inspector fires whether
    // or
    // not a transaction is active, so without app.current_tenants every can_access_tenant is false.
    // If this ever returns rows, the inspector stopped firing and every scoped assertion above
    // would
    // no longer prove anything. Ground truth: the rows exist, JDBC (inspector-free) still sees
    // them.
    @SuppressWarnings("unchecked")
    List<Object> scopedOut =
        em.createNativeQuery("SELECT d.document_id FROM documents d WHERE d.tenant_id = ?1")
            .setParameter(1, tenantA)
            .getResultList();
    assertTrue(scopedOut.isEmpty(), "an unscoped read of an active table must see nothing");
    assertEquals(
        2,
        (int)
            jdbc.queryForObject(
                "SELECT count(*) FROM documents WHERE tenant_id = ?", Integer.class, tenantA),
        "ground truth: both of A's documents exist, only the scope hides them from the read");
  }

  // --- scope helpers -------------------------------------------------------

  private List<RawDocument> rawAllDocumentsAs(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), documentRepository::rawAllDocuments);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private <T> T scoped(String tenantId, Supplier<T> read) {
    return tenantTx.execute(TxCtx.forTenant(tenantId), read);
  }

  private static List<String> idsOf(List<RawDocument> documents) {
    return documents.stream().map(RawDocument::getDocument_id).toList();
  }

  // --- seeding (auto-committed, inspector-free) ----------------------------

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private String seedDocument(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'text/plain', ?, ?)",
        id,
        "doc-native-" + id,
        id + ".txt",
        tenantId);
    return id;
  }

  private String seedTag(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "tag-" + id,
        tenantId);
    return id;
  }

  private String seedExercise(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "exercise-" + id,
        tenantId);
    return id;
  }

  private String seedScenario(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "scenario-" + id,
        tenantId);
    return id;
  }

  private String seedChannelWithLogo(String tenantId, String logoDocumentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, channel_logo_light,"
            + " tenant_id) VALUES (?, 'newsletter', ?, ?, ?)",
        id,
        "channel-" + id,
        logoDocumentId,
        tenantId);
    return id;
  }

  private String seedSecurityPlatformWithLogo(String tenantId, String logoDocumentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_type, asset_name, security_platform_logo_light,"
            + " tenant_id) VALUES (?, 'SecurityPlatform', ?, ?, ?)",
        id,
        "sp-" + id,
        logoDocumentId,
        tenantId);
    return id;
  }

  private String seedChallenge(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO challenges (challenge_id, tenant_id) VALUES (?, ?)", id, tenantId);
    return id;
  }

  private String seedPayloadWithFile(String tenantId, String fileDocumentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, file_drop_file, tenant_id)"
            + " VALUES (?, 'FileDrop', ?, ?, ?)",
        id,
        "payload-" + id,
        fileDocumentId,
        tenantId);
    return id;
  }

  private void seedArticleWithDocument(
      String channelId, String scenarioId, String exerciseId, String documentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, article_scenario, article_exercise,"
            + " article_name) VALUES (?, ?, ?, ?, ?)",
        id,
        channelId,
        scenarioId,
        exerciseId,
        "article-" + id);
    link("articles_documents", "article_id", id, "document_id", documentId);
  }

  private void link(
      String table, String leftColumn, String leftId, String rightColumn, String rightId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)",
        leftId,
        rightId);
  }
}
