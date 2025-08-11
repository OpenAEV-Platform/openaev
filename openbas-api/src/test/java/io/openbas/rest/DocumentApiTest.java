package io.openbas.rest;

import static io.openbas.rest.document.DocumentApi.DOCUMENT_API;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Challenge;
import io.openbas.database.model.Document;
import io.openbas.database.repository.ChallengeRepository;
import io.openbas.database.repository.DocumentRepository;
import io.openbas.rest.document.form.DocumentRelationsOutput;
import io.openbas.rest.document.form.RelatedEntityOutput;
import io.openbas.utils.fixtures.ChallengeFixture;
import io.openbas.utils.fixtures.DocumentFixture;
import io.openbas.utils.fixtures.FileFixture;
import io.openbas.utils.fixtures.PayloadFixture;
import io.openbas.utils.fixtures.composers.ChallengeComposer;
import io.openbas.utils.fixtures.composers.DocumentComposer;
import io.openbas.utils.fixtures.composers.PayloadComposer;
import io.openbas.utils.fixtures.files.BinaryFile;
import io.openbas.utils.mockUser.WithMockAdminUser;
import jakarta.annotation.Resource;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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
  @Autowired private MockMvc mvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ChallengeRepository challengeRepository;

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

    PayloadComposer.Composer payload =
        payloadComposer.forPayload(PayloadFixture.createDefaultExecutable());

    BinaryFile badCoffeeFileContent = FileFixture.getBadCoffeeFileContent();
    return documentComposer
        .forDocument(DocumentFixture.getDocument(badCoffeeFileContent))
        .withInMemoryFile(badCoffeeFileContent)
        .withPayloadExecutable(payload)
        .persist()
        .get();
  }

  @Nested
  @DisplayName("Documents CRUD")
  @WithMockAdminUser
  class CRUD {

    @Test
    @DisplayName("Given a document related to a payload should no delete the payload")
    void givenADocumentRelatedToAPayload_ShouldNoDeleteDocument() throws Exception {
      Document document = getDocumentWithPayload();

      mvc.perform(delete(DOCUMENT_API + "/" + document.getId())).andExpect(status().isBadRequest());

      Assertions.assertTrue(documentRepository.findById(document.getId()).isPresent());
    }

    @Test
    @DisplayName("Given a document without related entities should be deleted")
    void givenADocumentWithRelationsShouldBeDeleted() throws Exception {
      Document document = getDocumentWithChallenge();
      Challenge challenge = document.getChallenges().stream().findFirst().get();

      mvc.perform(delete(DOCUMENT_API + "/" + document.getId())).andExpect(status().isOk());

      assertFalse(documentRepository.findById(document.getId()).isPresent());
      assertTrue(challengeRepository.findById(challenge.getId()).isPresent());
    }

    @Test
    @DisplayName("Given a document id Should fetch related entities to this document")
    void givenDocumentShouldFetchRelatedEntities() throws Exception {
      Document document = getDocumentWithChallenge();
      Challenge challenge = document.getChallenges().stream().findFirst().get();

      String response =
          mvc.perform(get(DOCUMENT_API + "/" + document.getId() + "/relations"))
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
  }
}
