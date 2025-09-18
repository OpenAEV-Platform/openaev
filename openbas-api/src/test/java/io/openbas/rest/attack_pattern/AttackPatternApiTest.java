package io.openbas.rest.attack_pattern;

import static io.openbas.rest.attack_pattern.AttackPatternApi.ATTACK_PATTERN_URI;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import io.openbas.IntegrationTest;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.repository.AttackPatternRepository;
import io.openbas.ee.Ee;
import io.openbas.utils.fixtures.files.AttackPatternFixture;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.servlet.ServletException;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Transactional
@TestInstance(PER_CLASS)
public class AttackPatternApiTest extends IntegrationTest {
  @Autowired private Environment env;

  @MockBean private RestTemplate mockRestTemplate;
  @MockBean private Ee mockEe;

  @Autowired private MockMvc mvc;
  @Autowired private AttackPatternRepository attackPatternRepository;

  @Nested
  @DisplayName("Search Attack Patterns with AI Webservice")
  @WithMockUser(isAdmin = true)
  class AttackPatternWithTTPAIWebservice {

    @Test
    @DisplayName("Should throw an exception if no text and no files are provided")
    void given_noTextAndNoFiles_should_throwAnException() {
      MockPart jsonPart = new MockPart("text", "".getBytes());

      Exception exception =
          assertThrows(
              ServletException.class,
              () ->
                  mvc.perform(
                      multipart(ATTACK_PATTERN_URI + "/search-with-ai")
                          .part(jsonPart)
                          .contentType(MediaType.MULTIPART_FORM_DATA)));

      assertTrue(exception.getMessage().contains("Either files or text must be provided"));
    }

    @Test
    @DisplayName("Should throw an exception if more than 5 files are provided")
    void given_moreThan5Files_should_throwAnException() {
      MockPart jsonPart = new MockPart("text", "".getBytes());
      byte[] content = new byte[] {1, 2, 3, 4, 5}; // Example binary content
      MockMultipartFile mockFile =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);
      MockMultipartFile mockFile2 =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);
      MockMultipartFile mockFile3 =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);
      MockMultipartFile mockFile4 =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);
      MockMultipartFile mockFile5 =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);
      MockMultipartFile mockFile6 =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);

      Exception exception =
          assertThrows(
              ServletException.class,
              () ->
                  mvc.perform(
                      multipart(ATTACK_PATTERN_URI + "/search-with-ai")
                          .part(jsonPart)
                          .file(mockFile)
                          .file(mockFile2)
                          .file(mockFile3)
                          .file(mockFile4)
                          .file(mockFile5)
                          .file(mockFile6)
                          .contentType(MediaType.MULTIPART_FORM_DATA)));

      assertTrue(exception.getMessage().contains("Maximum of 5 files allowed"));
    }

    @Test
    @DisplayName("Should call AI webservice with text and files and return attackPatternsIds")
    void given_textAndFiles_should_returnAttackPatternsIds() throws Exception {
      AttackPattern attackPattern = AttackPatternFixture.createDefaultAttackPattern();
      attackPattern.setExternalId("T1057");
      AttackPattern attackPatternSaved = attackPatternRepository.save(attackPattern);

      String url = Objects.requireNonNull(env.getProperty("ttp.extraction.ai.webservice.url"));
      Mockito.when(mockRestTemplate.postForEntity(eq(url), any(), any()))
          .thenReturn(
              new ResponseEntity<>(
                  """
                          {
                          "mock-file.pdf": [
                            {
                              "text": "Another file's text chunk",
                              "predictions": {
                                "T1057": 0.92
                              }
                            }
                          ]
                        }""",
                  HttpStatus.OK));
      Mockito.when(mockEe.getEnterpriseEditionLicensePem()).thenReturn("mock-certificate");
      MockPart jsonPart = new MockPart("text", "Test".getBytes());
      byte[] content = new byte[] {1, 2, 3, 4, 5}; // Example binary content
      MockMultipartFile mockFile =
          new MockMultipartFile("files", "mock-file.pdf", "application/pdf", content);

      String response =
          mvc.perform(
                  multipart(ATTACK_PATTERN_URI + "/search-with-ai")
                      .part(jsonPart)
                      .file(mockFile)
                      .contentType(MediaType.MULTIPART_FORM_DATA))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotNull(response);
      assertTrue(response.contains(attackPatternSaved.getId()));
    }
  }
}
