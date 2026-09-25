package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static io.openaev.rest.document.DocumentApi.TENANT_DOCUMENT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The document create and update endpoints resolve the ids a caller supplies (simulations,
 * scenarios, tags) in the same tenant as the document they write.
 *
 * <p>On the non-prefixed route the write tenant comes from {@code X-Tenant-Ids} while the ambient
 * {@link TenantContext} stays on the default tenant, and simulations and scenarios are still scoped
 * by the ambient tenant. A lookup left on the ambient tenant would drop the associations of the
 * write tenant and attach parents of the default tenant to a document that belongs to another one.
 * The links are read from the join tables through raw JDBC, which the statement inspector never
 * rewrites.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents,tags")
@WithMockUser(isAdmin = true)
@DisplayName("Document write paths resolve their associations in the document tenant")
class DocumentWriteAssociationScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String UPSERT = "/upsert";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String exerciseInB;
  private String exerciseInDefault;
  private String scenarioInB;
  private String scenarioInDefault;
  private String tagInB;
  private String tagInDefault;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so nothing but the tenant of the lookup keeps a parent of
    // the default tenant away from a document written in B.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-association-b").getId();
    exerciseInB = saveExercise(tenantB);
    exerciseInDefault = saveExercise(Tenant.DEFAULT_TENANT_UUID);
    scenarioInB = saveScenario(tenantB);
    scenarioInDefault = saveScenario(Tenant.DEFAULT_TENANT_UUID);
    tagInB = saveTag(tenantB);
    tagInDefault = saveTag(Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();
    entityManager.clear();
    // Onboarding leaves B on the test thread, and the request thread of the header route carries
    // none: the ambient tenant must fall back to the default one here, or the header tests run
    // with B already ambient and the bridge they exist to pin never has anything to do.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void cleanStoredObjects() {
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

  @Nested
  @DisplayName("New document on the header route")
  class NewDocumentOnHeaderRoute {

    @Test
    @DisplayName(
        "given an upload scoped to B by the header when it names parents of B and of the default"
            + " tenant then only the parents of B are attached")
    void given_uploadOnHeaderRoute_should_attachOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      byte[] content = randomContent();

      // -- Act --
      String documentId =
          send(multipart(DOCUMENT_API), tenantB, content, randomName(), allParents());

      // -- Assert --
      assertThat(rowTenant(documentId)).isEqualTo(tenantB);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactly(exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactly(scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an upsert scoped to B by the header when it names parents of B and of the default"
            + " tenant then only the parents of B are attached")
    void given_upsertOnHeaderRoute_should_attachOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      byte[] content = randomContent();

      // -- Act --
      String documentId =
          send(multipart(DOCUMENT_API + UPSERT), tenantB, content, randomName(), allParents());

      // -- Assert --
      assertThat(rowTenant(documentId)).isEqualTo(tenantB);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactly(exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactly(scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Existing document on the header route")
  class ExistingDocumentOnHeaderRoute {

    @Test
    @DisplayName(
        "given a document of B with the same bytes when uploaded again with parents of both"
            + " tenants then only the parents of B are added")
    void given_sameBytesUploadedOnHeaderRoute_should_addOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      byte[] content = randomContent();
      String otherExerciseInB = saveExercise(tenantB);
      String otherScenarioInB = saveScenario(tenantB);
      String existing =
          saveDocumentInB(target(content), randomName(), otherExerciseInB, otherScenarioInB);

      // -- Act --
      String documentId =
          send(multipart(DOCUMENT_API), tenantB, content, randomName(), allParents());

      // -- Assert --
      assertThat(documentId).isEqualTo(existing);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactlyInAnyOrder(otherExerciseInB, exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactlyInAnyOrder(otherScenarioInB, scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given a document of B with the same bytes when upserted with parents of both tenants"
            + " then only the parents of B are added")
    void given_sameBytesUpsertedOnHeaderRoute_should_addOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      byte[] content = randomContent();
      String otherExerciseInB = saveExercise(tenantB);
      String otherScenarioInB = saveScenario(tenantB);
      String existing =
          saveDocumentInB(target(content), randomName(), otherExerciseInB, otherScenarioInB);

      // -- Act --
      String documentId =
          send(multipart(DOCUMENT_API + UPSERT), tenantB, content, randomName(), allParents());

      // -- Assert --
      assertThat(documentId).isEqualTo(existing);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactlyInAnyOrder(otherExerciseInB, exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactlyInAnyOrder(otherScenarioInB, scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given a document of B with the same name when upserted with new bytes and parents of"
            + " both tenants then only the parents of B are added")
    void given_sameNameUpsertedOnHeaderRoute_should_addOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      String name = randomName();
      String otherExerciseInB = saveExercise(tenantB);
      String otherScenarioInB = saveScenario(tenantB);
      String existing =
          saveDocumentInB(target(randomContent()), name, otherExerciseInB, otherScenarioInB);

      // -- Act --
      String documentId =
          send(multipart(DOCUMENT_API + UPSERT), tenantB, randomContent(), name, allParents());

      // -- Assert --
      assertThat(documentId).isEqualTo(existing);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactlyInAnyOrder(otherExerciseInB, exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactlyInAnyOrder(otherScenarioInB, scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("New document on the prefixed route")
  class NewDocumentOnPrefixedRoute {

    @Test
    @DisplayName(
        "given an upload under the path of B when it names parents of B and of the default tenant"
            + " then only the parents of B are attached")
    void given_uploadOnPrefixedRoute_should_attachOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      byte[] content = randomContent();

      // -- Act --
      String documentId =
          send(multipart(TENANT_DOCUMENT_API, tenantB), null, content, randomName(), allParents());

      // -- Assert --
      assertThat(rowTenant(documentId)).isEqualTo(tenantB);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactly(exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactly(scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an upsert under the path of B when it names parents of B and of the default tenant"
            + " then only the parents of B are attached")
    void given_upsertOnPrefixedRoute_should_attachOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      byte[] content = randomContent();

      // -- Act --
      String documentId =
          send(
              multipart(TENANT_DOCUMENT_API + UPSERT, tenantB),
              null,
              content,
              randomName(),
              allParents());

      // -- Assert --
      assertThat(rowTenant(documentId)).isEqualTo(tenantB);
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactly(exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactly(scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Update of a document of B")
  class UpdateDocument {

    @Test
    @DisplayName(
        "given an update scoped to B by the header when it names parents of B and of the default"
            + " tenant then the document keeps the parents of B and gains none of the default"
            + " tenant")
    void given_updateOnHeaderRoute_should_keepOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      String documentId =
          saveDocumentInB(target(randomContent()), randomName(), exerciseInB, scenarioInB);

      // -- Act --
      mvc.perform(
              put(DOCUMENT_API + "/" + documentId)
                  .header(TENANT_HEADER, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(allParents()))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactly(exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactly(scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an update under the path of B when it names parents of B and of the default tenant"
            + " then the document keeps the parents of B and gains none of the default tenant")
    void given_updateOnPrefixedRoute_should_keepOnlyWriteTenantParents() throws Exception {
      // -- Arrange --
      String documentId =
          saveDocumentInB(target(randomContent()), randomName(), exerciseInB, scenarioInB);

      // -- Act --
      mvc.perform(
              put(TENANT_DOCUMENT_API + "/" + documentId, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(allParents()))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(linked("exercises_documents", "exercise_id", documentId))
          .containsExactly(exerciseInB);
      assertThat(linked("scenarios_documents", "scenario_id", documentId))
          .containsExactly(scenarioInB);
      assertThat(linked("documents_tags", "tag_id", documentId)).containsExactly(tagInB);
    }
  }

  // -- Helpers --

  private Map<String, Object> allParents() {
    return Map.of(
        "document_exercises", List.of(exerciseInB, exerciseInDefault),
        "document_scenarios", List.of(scenarioInB, scenarioInDefault),
        "document_tags", List.of(tagInB, tagInDefault));
  }

  private String send(
      MockMultipartHttpServletRequestBuilder request,
      String tenantHeader,
      byte[] content,
      String fileName,
      Map<String, Object> input)
      throws Exception {
    if (tenantHeader != null) {
      request.header(TENANT_HEADER, tenantHeader);
    }
    MockPart inputPart = new MockPart("input", objectMapper.writeValueAsBytes(input));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    MockMultipartFile filePart =
        new MockMultipartFile("file", fileName, MediaType.TEXT_PLAIN_VALUE, content);
    String response =
        mvc.perform(request.part(inputPart).file(filePart).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = JsonPath.read(response, "$.document_id");
    uploadedObjects.add(new String[] {rowTenant(documentId), target(content)});
    return documentId;
  }

  private static byte[] randomContent() {
    return ("doc-association-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
  }

  private static String randomName() {
    return "doc-association-" + UUID.randomUUID() + ".txt";
  }

  private static String target(byte[] content) {
    return DigestUtils.md5Hex(content) + ".txt";
  }

  private String saveExercise(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    return exerciseRepository.save(exercise).getId();
  }

  private String saveScenario(String tenantId) {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantId));
    return scenarioRepository.save(scenario).getId();
  }

  private String saveTag(String tenantId) {
    Tag tag = TagFixture.getTagWithText("doc-association-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    return tagRepository.save(tag).getId();
  }

  private String saveDocumentInB(
      String target, String name, String linkedExerciseId, String linkedScenarioId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setTenant(new Tenant(tenantB));
    document.setTarget(target);
    document.setName(name);
    document.setExercises(
        new HashSet<>(Set.of(entityManager.getReference(Exercise.class, linkedExerciseId))));
    document.setScenarios(
        new HashSet<>(Set.of(entityManager.getReference(Scenario.class, linkedScenarioId))));
    String documentId = documentRepository.save(document).getId();
    entityManager.flush();
    entityManager.clear();
    return documentId;
  }

  private String rowTenant(String documentId) {
    return column("SELECT tenant_id FROM documents WHERE document_id = ?", documentId).get(0);
  }

  private List<String> linked(String joinTable, String column, String documentId) {
    return column(
        "SELECT " + column + " FROM " + joinTable + " WHERE document_id = ? ORDER BY 1",
        documentId);
  }

  /** Raw JDBC on the test connection: the statement inspector never rewrites it. */
  private List<String> column(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> values = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
              return values;
            });
  }
}
