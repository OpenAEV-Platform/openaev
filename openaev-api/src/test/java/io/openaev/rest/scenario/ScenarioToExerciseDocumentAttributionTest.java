package io.openaev.rest.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.MinioService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Converting a scenario to an exercise keeps each cloned document under the source document's
 * tenant. The clone copies the source with {@code BeanUtils.copyProperties}, which carries both the
 * id and the tenant, so the save is a merge of the existing row rather than an insert: {@code
 * TenantBaseListener.prePersist} never runs for it (and is gone entirely since {@code documents}
 * activated), and the row keeps its tenant whatever the ambient {@link TenantContext}. This pins
 * that the document attribution does not depend on the listener removed at {@code documents}
 * go-live.
 *
 * <p>{@code @TestPropertySource} activates {@code documents} for this test only (the test classpath
 * keeps the allowlist empty), so the conversion runs against the statement inspector. The class is
 * deliberately NOT {@code @Transactional}: a single test transaction keeps the scenario and its
 * document in the persistence context, so {@code toExercise} would read them from the first-level
 * cache and never reach the inspector; the row would then keep its tenant whatever the scope,
 * masking the very behaviour this pins. Seeding is committed through the background primitive (so
 * the document write lands under the source tenant's v2 scope) and cleaned up by tenant.
 *
 * <p>The conversion is run under the source tenant's v2 scope (as the HTTP and scheduled callers
 * do), but with the ambient {@link TenantContext} set to a THIRD tenant, distinct from the source
 * and from any expected value, so that no ambient fallback can attribute the clone: only the
 * merge-carried tenant of the source can produce it.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Scenario to exercise conversion keeps documents under their source tenant")
class ScenarioToExerciseDocumentAttributionTest extends IntegrationTest {

  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private MinioService minioService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantThird;
  private String scenarioId;
  private String documentId;
  private String target;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seed() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = tenantHelper.createTenantWithCurrentUser("t22-clone-a").getId();
    tenantThird = tenantHelper.createTenantWithCurrentUser("t22-clone-third").getId();

    byte[] bytes = ("t22-clone-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    target = DigestUtils.md5Hex(bytes) + ".jpg";
    minioService.uploadFileForTenant(
        tenantA, target, new ByteArrayInputStream(bytes), bytes.length, MediaType.IMAGE_JPEG_VALUE);
    uploadedObjects.add(new String[] {tenantA, target});

    // Committed seeding under the source tenant's v2 scope, so the document write lands under A and
    // is out of the persistence context when the conversion re-reads it.
    tenantScopedJobRunner.runInTenant(
        tenantA,
        () -> {
          Scenario scenario = ScenarioFixture.getScenario();
          scenario.setTenant(new Tenant(tenantA));
          Scenario scenarioSaved = scenarioService.createScenario(scenario);

          Document document = DocumentFixture.getDocumentJpeg();
          document.setTenant(new Tenant(tenantA));
          document.setTarget(target);
          Document documentSaved = documentRepository.save(document);

          scenarioSaved.setDocuments(new ArrayList<>(List.of(documentSaved)));
          scenarioRepository.save(scenarioSaved);
          entityManager.flush();

          scenarioId = scenarioSaved.getId();
          documentId = documentSaved.getId();
        });
    entityManager.clear();
  }

  @AfterEach
  void cleanup() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
    // Child rows first: the mail reply-to element-collections and the document link tables carry a
    // foreign key to their tenant-scoped parent, which must be cleared before the parent row.
    jdbc.update(
        "DELETE FROM exercise_mails_reply_to WHERE exercise_id IN (SELECT exercise_id FROM"
            + " exercises WHERE tenant_id IN (?, ?))",
        tenantA,
        tenantThird);
    jdbc.update(
        "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN (SELECT scenario_id FROM"
            + " scenarios WHERE tenant_id IN (?, ?))",
        tenantA,
        tenantThird);
    jdbc.update(
        "DELETE FROM exercises_documents WHERE exercise_id IN (SELECT exercise_id FROM exercises"
            + " WHERE tenant_id IN (?, ?))",
        tenantA,
        tenantThird);
    jdbc.update(
        "DELETE FROM scenarios_documents WHERE scenario_id IN (SELECT scenario_id FROM scenarios"
            + " WHERE tenant_id IN (?, ?))",
        tenantA,
        tenantThird);
    jdbc.update("DELETE FROM exercises WHERE tenant_id IN (?, ?)", tenantA, tenantThird);
    jdbc.update("DELETE FROM scenarios WHERE tenant_id IN (?, ?)", tenantA, tenantThird);
    jdbc.update("DELETE FROM documents WHERE tenant_id IN (?, ?)", tenantA, tenantThird);
    TenantContext.clearCurrentTenant();
    // Both tenants are committed by the helper (the class is not transactional): remove them with
    // their memberships and onboarding rows, so they do not accumulate in the shared database.
    tenantHelper.deleteCommittedTenants(tenantA, tenantThird);
  }

  @Test
  @DisplayName("given_scenarioDocumentInTenantA_should_keepClonedDocumentAndObjectInTenantA")
  void given_scenarioDocumentInTenantA_should_keepClonedDocumentAndObjectInTenantA() {
    // Act: convert under the source tenant's v2 scope (the caller's contract), with the ambient
    // TenantContext pointed at a third tenant so only a merge-carried tenant can attribute the
    // clone.
    Exercise exercise =
        tenantScopedJobRunner.supplyInTenant(
            tenantA,
            () -> {
              Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
              TenantContext.setCurrentTenant(tenantThird);
              return scenarioToExerciseService.toExercise(scenario, null, false);
            });

    // Assert: the exercise references the same document row (a merge, so no prePersist), still in
    // A.
    String clonedDocumentId = exerciseDocumentId(exercise.getId());
    assertEquals(
        documentId,
        clonedDocumentId,
        "the conversion merges the existing document row rather than inserting a new one");
    assertEquals(
        tenantA,
        rowTenant(clonedDocumentId),
        "the cloned document must stay under the source tenant (A), not the ambient third tenant");
    assertEquals(
        1,
        minioService.countObjects(tenantA + "/" + target),
        "the document object must stay under the source tenant's prefix");
  }

  private String exerciseDocumentId(String exerciseId) {
    return jdbc.queryForObject(
        "SELECT document_id FROM exercises_documents WHERE exercise_id = ?",
        String.class,
        exerciseId);
  }

  private String rowTenant(String documentId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM documents WHERE document_id = ?", String.class, documentId);
  }
}
