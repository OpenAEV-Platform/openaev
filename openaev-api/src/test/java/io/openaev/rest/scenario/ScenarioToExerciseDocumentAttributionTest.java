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
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converting a scenario to an exercise keeps each cloned document under the source document's
 * tenant. The clone copies the source with {@code BeanUtils.copyProperties}, which carries both the
 * id and the tenant, so the save is a merge of the existing row rather than an insert: {@code
 * TenantBaseListener.prePersist} never runs for it, and the row keeps its tenant whatever the
 * ambient {@link TenantContext}. This pins that the document attribution does not depend on the
 * listener removed at {@code documents} go-live.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Scenario to exercise conversion keeps documents under their source tenant")
class ScenarioToExerciseDocumentAttributionTest extends IntegrationTest {

  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private String tenantA;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTenant() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("t22-clone-a").getId();
  }

  @AfterEach
  void clearContext() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_scenarioDocumentInTenantA_should_keepClonedDocumentAndObjectInTenantA")
  void given_scenarioDocumentInTenantA_should_keepClonedDocumentAndObjectInTenantA()
      throws Exception {
    // Arrange: a scenario and its document live in tenant A; the document object is under A's
    // prefix.
    TenantContext.setCurrentTenant(tenantA);
    byte[] bytes = ("t22-clone-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".jpg";
    minioService.uploadFileForTenant(
        tenantA, target, new ByteArrayInputStream(bytes), bytes.length, MediaType.IMAGE_JPEG_VALUE);
    uploadedObjects.add(new String[] {tenantA, target});

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

    // Act
    Exercise exercise = scenarioToExerciseService.toExercise(scenarioSaved, null, false);
    entityManager.flush();

    // Assert: the exercise references the same document row (a merge, so no prePersist), still in
    // A.
    String clonedDocumentId = exerciseDocumentId(exercise.getId());
    assertEquals(
        documentSaved.getId(),
        clonedDocumentId,
        "the conversion merges the existing document row rather than inserting a new one");
    assertEquals(
        tenantA,
        rowTenant(clonedDocumentId),
        "the cloned document must stay under the source tenant (A), not the ambient tenant");
    assertEquals(
        1,
        minioService.countObjects(tenantA + "/" + target),
        "the document object must stay under the source tenant's prefix");
  }

  private String exerciseDocumentId(String exerciseId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT document_id FROM exercises_documents WHERE exercise_id = ?1")
            .setParameter(1, exerciseId)
            .getSingleResult();
  }

  private String rowTenant(String documentId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM documents WHERE document_id = ?1")
            .setParameter(1, documentId)
            .getSingleResult();
  }
}
