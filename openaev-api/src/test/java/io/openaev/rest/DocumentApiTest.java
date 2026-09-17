package io.openaev.rest;

import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.processor.datapack.PresetTenantData;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.rest.document.form.DocumentRelationsOutput;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.BinaryFile;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Document API Integration Tests")
class DocumentApiTest extends IntegrationTest {

  @Resource protected ObjectMapper mapper;
  @Autowired DocumentComposer documentComposer;
  @Autowired ChallengeComposer challengeComposer;
  @Autowired PayloadComposer payloadComposer;
  @Autowired DomainComposer domainComposer;
  @Autowired ScenarioComposer scenarioComposer;
  @Autowired ExerciseComposer exerciseComposer;
  @Autowired SecurityPlatformComposer securityPlatformComposer;
  @Autowired private MockMvc mvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private EntityManager entityManager;

  /**
   * Mirror of the platform's real Observer default role capability set (kept equal to {@link
   * PresetTenantData#DEFAULT_ROLES} via a drift-guard test). Used to bind the {@code @WithMockUser}
   * default-role non-regression test to the production source of truth.
   */
  private static final Set<Capability> OBSERVER_DEFAULT_CAPABILITIES =
      Set.of(
          Capability.ACCESS_ASSESSMENT,
          Capability.ACCESS_ASSETS,
          Capability.ACCESS_CREDENTIALS,
          Capability.ACCESS_MARKING_DEFINITION,
          Capability.ACCESS_THREAT_ARSENALS,
          Capability.ACCESS_DASHBOARDS,
          Capability.ACCESS_REPORTINGS,
          Capability.ACCESS_FINDINGS,
          Capability.ACCESS_DOCUMENTS,
          Capability.ACCESS_CHANNELS,
          Capability.ACCESS_PHISHING,
          Capability.ACCESS_CHALLENGES,
          Capability.ACCESS_LESSONS_LEARNED,
          Capability.ACCESS_SECURITY_PLATFORMS);

  @BeforeAll
  void beforeAll() {
    challengeComposer.reset();
    documentComposer.reset();
  }

  @AfterAll
  void afterAll() {
    challengeComposer.reset();
    documentComposer.reset();
  }

  private Document getDocumentWithChallenge() {

    ChallengeComposer.Composer challenge =
        challengeComposer.forChallenge(ChallengeFixture.createDefaultChallenge());

    BinaryFile badCoffeeFileContent = FileFixture.getBadCoffeeFileContent();
    return documentComposer
        .forDocument(DocumentFixture.getDocument(badCoffeeFileContent))
        .withInMemoryFile(badCoffeeFileContent)
        .withChallenge(challenge)
        .persist()
        .get();
  }

  private Document getDocumentWithPayload() {
    BinaryFile badCoffeeFileContent = FileFixture.getBadCoffeeFileContent();
    Document document =
        documentComposer
            .forDocument(DocumentFixture.getDocument(badCoffeeFileContent))
            .withInMemoryFile(badCoffeeFileContent)
            .persist()
            .get();

    payloadComposer.forPayload(PayloadFixture.createDefaultExecutable(document)).persist();
    entityManager.flush();
    entityManager.clear();

    return documentRepository.findById(document.getId()).orElseThrow();
  }

  private Document getDocumentUsedAsSecurityPlatformLogo() {
    BinaryFile badCoffeeFileContent = FileFixture.getBadCoffeeFileContent();
    Document document =
        documentComposer
            .forDocument(DocumentFixture.getDocument(badCoffeeFileContent))
            .withInMemoryFile(badCoffeeFileContent)
            .persist()
            .get();

    // Collectors upload their platform logo as a Document and reference it from the
    // security platform: such documents must be protected from deletion.
    SecurityPlatform securityPlatform =
        SecurityPlatformFixture.createDefault(
            "PlatformWithLogo", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name());
    securityPlatform.setLogoLight(document);
    securityPlatformComposer.forSecurityPlatform(securityPlatform).persist();

    return document;
  }

  private Document getSimpleDocumentWithInMemoryFile() {
    BinaryFile badCoffeeFileContent = FileFixture.getBadCoffeeFileContent();
    return documentComposer
        .forDocument(DocumentFixture.getDocument(badCoffeeFileContent))
        .withInMemoryFile(badCoffeeFileContent)
        .persist()
        .get();
  }

  @Nested
  @DisplayName("Download document file RBAC (GET /documents/{id}/file)")
  class DownloadDocumentFileAccessControl {

    /**
     * Non-regression on the human path (behaviour identical to pre-#294): a user whose role carries
     * ACCESS_DOCUMENTS (READ on DOCUMENT), e.g. Observer, downloads the file.
     */
    @Test
    @DisplayName("Given a human user with ACCESS_DOCUMENTS should download the file")
    @WithMockUser(withCapabilities = {Capability.ACCESS_DOCUMENTS})
    void given_human_user_with_access_documents_capability_should_download() throws Exception {
      Document document = getSimpleDocumentWithInMemoryFile();

      byte[] response =
          mvc.perform(get(DOCUMENT_API + "/" + document.getId() + "/file").with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      assertArrayEquals(FileFixture.getBadCoffeeFileContent().getContentBytes(), response);
    }

    /**
     * Non-regression on the human path (behaviour identical to pre-#294): a user without
     * ACCESS_DOCUMENTS is refused on the human download route.
     */
    @Test
    @DisplayName("Given a human user without ACCESS_DOCUMENTS should be forbidden")
    @WithMockUser
    void given_human_user_without_access_documents_capability_should_be_forbidden()
        throws Exception {
      Document document = getSimpleDocumentWithInMemoryFile();

      mvc.perform(get(DOCUMENT_API + "/" + document.getId() + "/file").with(csrf()))
          .andExpect(status().isForbidden());
    }

    /**
     * #294 behaviour change (closes the vector on the ORIGINAL route): the service-account holds
     * only {AGENT_RUNTIME_ACCESS, AGENT_DOCUMENT_ACCESS} and no longer ACCESS_DOCUMENTS, so the
     * legacy human route /documents/{id}/file (gated by ACCESS_DOCUMENTS/READ) now returns 403 for
     * it. The service-account must use the dedicated /agent-file route instead. This test is what
     * concretely proves the old route is no longer reachable with the service-account capability
     * set.
     */
    @Test
    @DisplayName("Given a service-account capability set should no longer download via /file")
    @WithMockUser(
        withCapabilities = {Capability.AGENT_RUNTIME_ACCESS, Capability.AGENT_DOCUMENT_ACCESS})
    void given_service_account_token_should_no_longer_download_via_original_endpoint()
        throws Exception {
      Document document = getSimpleDocumentWithInMemoryFile();

      mvc.perform(get(DOCUMENT_API + "/" + document.getId() + "/file").with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  /**
   * Non-regression for standard HUMAN users carrying the platform's REAL default "Observer" role
   * (as provisioned by {@link io.openaev.processor.datapack.PresetTenantData#DEFAULT_ROLES}), NOT a
   * generic single-capability mock. The #294 fix must not degrade the human document experience: an
   * Observer still lists, searches and downloads documents exactly as before.
   *
   * <p>There is no existing test mechanism that provisions the named default roles for a tenant and
   * authenticates against REST endpoints (the migration test {@code
   * V20260811_..._to_default_rolesTest} materializes a named role but exercises a RuntimeMigration,
   * not MockMvc). We therefore reuse the existing {@code @WithMockUser} mechanism, but feed it the
   * EXACT Observer capability set and bind it to the source of truth with a drift guard below.
   */
  @Nested
  @DisplayName("Default Observer role non-regression on documents")
  class DefaultObserverRoleNonRegression {

    /**
     * Guard: the capability set hard-coded in {@link
     * #given_observer_default_role_should_still_list_and_search_documents}'s {@code @WithMockUser}
     * must stay equal to the live platform preset. If the Observer default role changes in {@link
     * io.openaev.processor.datapack.PresetTenantData}, this fails and forces the annotation to be
     * updated, so the test always exercises the REAL default role.
     */
    @Test
    @DisplayName("Observer capability set under test matches the platform preset (drift guard)")
    void observer_capability_set_under_test_should_match_preset() {
      assertEquals(
          PresetTenantData.DEFAULT_ROLES.get("Observer"),
          OBSERVER_DEFAULT_CAPABILITIES,
          "The Observer default role changed in PresetTenantData.DEFAULT_ROLES: update the"
              + " @WithMockUser(withCapabilities = {...}) set on this test accordingly.");
    }

    @Test
    @DisplayName("Given the default Observer role should still list, search and download documents")
    // Full real Observer default role (PresetTenantData.DEFAULT_ROLES.get("Observer")) — kept in
    // sync with OBSERVER_DEFAULT_CAPABILITIES via the drift guard above.
    @WithMockUser(
        withCapabilities = {
          Capability.ACCESS_ASSESSMENT,
          Capability.ACCESS_ASSETS,
          Capability.ACCESS_CREDENTIALS,
          Capability.ACCESS_MARKING_DEFINITION,
          Capability.ACCESS_THREAT_ARSENALS,
          Capability.ACCESS_DASHBOARDS,
          Capability.ACCESS_REPORTINGS,
          Capability.ACCESS_FINDINGS,
          Capability.ACCESS_DOCUMENTS,
          Capability.ACCESS_CHANNELS,
          Capability.ACCESS_PHISHING,
          Capability.ACCESS_CHALLENGES,
          Capability.ACCESS_LESSONS_LEARNED,
          Capability.ACCESS_SECURITY_PLATFORMS
        })
    void given_observer_default_role_should_still_list_and_search_documents() throws Exception {
      Document document = getSimpleDocumentWithInMemoryFile();

      // -- Listing: GET /api/documents (SEARCH on DOCUMENT -> ACCESS_DOCUMENTS)
      mvc.perform(get(DOCUMENT_API).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk());

      // -- Search: POST /api/documents/search (SEARCH on DOCUMENT -> ACCESS_DOCUMENTS)
      mvc.perform(
              post(DOCUMENT_API + "/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(PaginationFixture.getDefault().build()))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Download: GET /api/documents/{id}/file (READ on DOCUMENT -> ACCESS_DOCUMENTS). Already
      // covered generically by the RBAC suite; re-verified here in the real default-role context.
      byte[] file =
          mvc.perform(get(DOCUMENT_API + "/" + document.getId() + "/file").with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      assertArrayEquals(FileFixture.getBadCoffeeFileContent().getContentBytes(), file);
    }
  }

  @Nested
  @DisplayName("Documents CRUD")
  @WithMockUser(isAdmin = true)
  class CRUD {

    @Test
    @DisplayName("Given a document related to a payload should no delete the payload")
    void givenADocumentRelatedToAPayload_ShouldNoDeleteDocument() throws Exception {
      Document document = getDocumentWithPayload();

      mvc.perform(delete(DOCUMENT_API + "/" + document.getId()).with(csrf()))
          .andExpect(status().isBadRequest());

      Assertions.assertTrue(documentRepository.findById(document.getId()).isPresent());
    }

    @Test
    @DisplayName("Given a document used as a security platform logo should not delete the document")
    void givenADocumentUsedAsSecurityPlatformLogo_ShouldNotDeleteDocument() throws Exception {
      Document document = getDocumentUsedAsSecurityPlatformLogo();
      // Reload from DB so the document's reverse logo collections are populated
      entityManager.flush();
      entityManager.clear();

      mvc.perform(delete(DOCUMENT_API + "/" + document.getId()).with(csrf()))
          .andExpect(status().isBadRequest());

      Assertions.assertTrue(documentRepository.findById(document.getId()).isPresent());
    }

    @Test
    @DisplayName("Given a document without related entities should be deleted")
    void givenADocumentWithRelationsShouldBeDeleted() throws Exception {
      Document document = getDocumentWithChallenge();
      Challenge challenge = document.getChallenges().stream().findFirst().get();

      mvc.perform(delete(DOCUMENT_API + "/" + document.getId()).with(csrf()))
          .andExpect(status().isOk());

      assertFalse(documentRepository.findById(document.getId()).isPresent());
      assertTrue(challengeRepository.findById(challenge.getId()).isPresent());
    }

    @Test
    @DisplayName("Given a document id Should fetch related entities to this document")
    void givenDocumentShouldFetchRelatedEntities() throws Exception {
      Document document = getDocumentWithChallenge();
      Challenge challenge = document.getChallenges().stream().findFirst().get();

      String response =
          mvc.perform(get(DOCUMENT_API + "/" + document.getId() + "/relations").with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotNull(response);

      DocumentRelationsOutput output =
          DocumentRelationsOutput.builder()
              .challenges(
                  Set.of(new RelatedEntityOutput(challenge.getId(), challenge.getName(), null)))
              .build();

      String relationJson = mapper.writeValueAsString(output);

      assertThatJson(response).when(IGNORING_ARRAY_ORDER).isEqualTo(relationJson);
    }

    @Test
    @DisplayName("Should create a document when uploading a valid file and input")
    void uploadDocumentShouldCreateDocument() throws Exception {
      // -- PREPARE
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      Exercise exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

      DocumentCreateInput input = new DocumentCreateInput();
      input.setDescription("My test document");
      input.setScenarioIds(List.of(scenario.getId()));
      input.setExerciseIds(List.of(exercise.getId()));

      MockPart inputPart = new MockPart("input", mapper.writeValueAsBytes(input));
      inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

      MockMultipartFile filePart =
          new MockMultipartFile(
              "file",
              FileFixture.getPngSmileFileContent().getFileName(),
              MediaType.APPLICATION_XML.toString(),
              FileFixture.getPngSmileFileContent().getContentBytes());

      // -- EXECUTE
      String response =
          mvc.perform(
                  multipart(DOCUMENT_API + "/upsert")
                      .part(inputPart)
                      .file(filePart)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- VERIFY
      assertNotNull(response);
      assertEquals(
          FileFixture.getPngSmileFileContent().getFileName(),
          JsonPath.read(response, "$.document_name"));
      assertEquals("My test document", JsonPath.read(response, "$.document_description"));
      assertEquals(scenario.getId(), JsonPath.read(response, "$.document_scenarios[0]"));
      assertEquals(exercise.getId(), JsonPath.read(response, "$.document_exercises[0]"));
    }

    @Test
    @DisplayName("Should update a document when uploading a valid file and input")
    void uploadDocumentShouldUpdateDocument() throws Exception {
      // -- PREPARE
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      Exercise exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

      Document document =
          documentComposer
              .forDocument(DocumentFixture.getDocument(FileFixture.getPlainTextFileContent()))
              .persist()
              .get();
      document.setExercises(new HashSet<>(Set.of(exercise)));
      document.setScenarios(new HashSet<>(Set.of(scenario)));
      documentRepository.save(document);

      DocumentCreateInput input = new DocumentCreateInput();
      input.setDescription("My test document");
      input.setScenarioIds(List.of(scenario.getId()));
      input.setExerciseIds(List.of(exercise.getId()));

      MockPart inputPart = new MockPart("input", mapper.writeValueAsBytes(input));
      inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

      MockMultipartFile filePart =
          new MockMultipartFile(
              "file",
              document.getName(),
              MediaType.APPLICATION_XML.toString(),
              FileFixture.getPlainTextFileContent().getContentBytes());

      // -- EXECUTE
      String response =
          mvc.perform(
                  multipart(DOCUMENT_API + "/upsert")
                      .part(inputPart)
                      .file(filePart)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- VERIFY
      assertNotNull(response);
      assertEquals(document.getName(), JsonPath.read(response, "$.document_name"));
      assertEquals("My test document", JsonPath.read(response, "$.document_description"));
      assertEquals(scenario.getId(), JsonPath.read(response, "$.document_scenarios[0]"));
      assertEquals(exercise.getId(), JsonPath.read(response, "$.document_exercises[0]"));
    }

    @Test
    @DisplayName("Should update a document by target id when uploading a valid file and input")
    void uploadDocumentShouldUpdateDocumentByTargetId() throws Exception {
      // -- PREPARE
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      Exercise exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

      Document document =
          documentComposer
              .forDocument(DocumentFixture.getDocument(FileFixture.getPlainTextFileContent()))
              .persist()
              .get();
      document.setExercises(new HashSet<>(Set.of(exercise)));
      document.setScenarios(new HashSet<>(Set.of(scenario)));

      String extension = FilenameUtils.getExtension(document.getName());
      String fileTarget =
          DigestUtils.md5Hex(
                  new ByteArrayInputStream(FileFixture.getPlainTextFileContent().getContentBytes()))
              + "."
              + extension;
      document.setDescription("My test document");
      document.setTarget(fileTarget);
      documentRepository.save(document);

      DocumentCreateInput input = new DocumentCreateInput();
      input.setDescription("Should not have this description");
      input.setScenarioIds(List.of(scenario.getId()));
      input.setExerciseIds(List.of(exercise.getId()));

      MockPart inputPart = new MockPart("input", mapper.writeValueAsBytes(input));
      inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

      MockMultipartFile filePart =
          new MockMultipartFile(
              "file",
              document.getName(),
              MediaType.APPLICATION_XML.toString(),
              FileFixture.getPlainTextFileContent().getContentBytes());

      // -- EXECUTE
      String response =
          mvc.perform(
                  multipart(DOCUMENT_API + "/upsert")
                      .part(inputPart)
                      .file(filePart)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- VERIFY
      assertNotNull(response);
      assertEquals(document.getName(), JsonPath.read(response, "$.document_name"));
      assertEquals("My test document", JsonPath.read(response, "$.document_description"));
      assertEquals(scenario.getId(), JsonPath.read(response, "$.document_scenarios[0]"));
      assertEquals(exercise.getId(), JsonPath.read(response, "$.document_exercises[0]"));
    }
  }

  @Test
  public void encodeDocumentName() {
    Map<String, String> map = new HashMap<>();
    map.put("rapport final.pdf", "rapport%20final.pdf");
    map.put("photo_été.jpeg", "photo_%C3%A9t%C3%A9.jpeg");
    map.put("notes (version 2).txt", "notes%20%28version%202%29.txt");
    map.put("résumé📄.docx", "r%C3%A9sum%C3%A9%F0%9F%93%84.docx");
    map.put("code-source#1.rs", "code-source%231.rs");
    map.put("données_brutes.csv", "donn%C3%A9es_brutes.csv");
    map.put("archive-2025!.zip", "archive-2025%21.zip");
    map.put("🎵_musique.mp3", "%F0%9F%8E%B5_musique.mp3");
    map.put("image@2x.png", "image%402x.png");
    map.put("backup&save.tar.gz", "backup%26save.tar.gz");

    map.put("회의록.docx", "%ED%9A%8C%EC%9D%98%EB%A1%9D.docx");
    map.put("사진_여름.png", "%EC%82%AC%EC%A7%84_%EC%97%AC%EB%A6%84.png");
    map.put("음악🎶.mp3", "%EC%9D%8C%EC%95%85%F0%9F%8E%B6.mp3");

    map.put("报告.pdf", "%E6%8A%A5%E5%91%8A.pdf");
    map.put("照片_夏天.jpg", "%E7%85%A7%E7%89%87_%E5%A4%8F%E5%A4%A9.jpg");
    map.put("音乐文件.mp3", "%E9%9F%B3%E4%B9%90%E6%96%87%E4%BB%B6.mp3");
    for (Map.Entry<String, String> name : map.entrySet()) {
      assertEquals(DocumentService.encodeFileName(name.getKey()), name.getValue());
    }
  }
}
