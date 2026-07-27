package io.openaev.aop.audit_log;

import static io.openaev.aop.audit_log.AuditLogTestHelper.setupFileAppender;
import static io.openaev.aop.audit_log.AuditLogTestHelper.teardownFileAppender;
import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.rest.document.form.DocumentUpdateInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(
    properties = {
      "openaev.audit-logs.transports=file",
      "openaev.audit-logs.halt-on-failure=false",
      "AUDIT_LOG_DIR=target/test-audit-log-doc"
    })
class AuditLoggerDocumentTest extends IntegrationTest {

  private static final Path AUDIT_LOG_FILE = Paths.get("target/test-audit-log-doc/audit.log");
  private static final String TEST_APPENDER_NAME = "AUDIT_LOG_DOCUMENT_E2E_TEST_APPENDER";

  @Autowired private MockMvc mvc;
  @Autowired private AuditLogger auditLogger;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeAll
  void setupAuditFileAppender() throws Exception {
    setupFileAppender(AUDIT_LOG_FILE, TEST_APPENDER_NAME);
  }

  @AfterAll
  void teardownAuditFileAppender() {
    teardownFileAppender(TEST_APPENDER_NAME);
  }

  @BeforeEach
  void setupTest() throws Exception {
    Mockito.when(enterpriseEditionService.isLicenseActive(Mockito.any())).thenReturn(true);
    assertThat(auditLogger.isAuditLoggingEnabled()).isTrue();
    Files.writeString(
        AUDIT_LOG_FILE,
        "",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
  }

  @Nested
  @DisplayName("Document lifecycle")
  class DocumentLifecycleAudit {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_DOCUMENTS})
    void given_newDocumentUpload_should_logUpdateScope() throws Exception {
      // Arrange
      String fileName = "audit-create-" + UUID.randomUUID() + ".txt";
      String content = "create-content-" + UUID.randomUUID();
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      uploadDocument(fileName, "Create description", content);

      // Assert
      String newContent =
          assertAuditLogContainsNewContent(
              sizeBefore,
              "\"event_scope\" : \"update\"",
              "\"method\" : \"POST\"",
              "\"url\" : \"http://localhost/api/documents\"",
              "\"entity_type\" : \"Document\"");
      assertThat(newContent).contains("\"message\" : \"updates Document\"");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_DOCUMENTS})
    void given_documentMetadataUpdate_should_notLogChangedInputField() throws Exception {
      // Arrange
      String fileName = "audit-update-" + UUID.randomUUID() + ".txt";
      String oldDescription = "Initial description";
      String newDescription = "Updated description";
      String uploadResponse =
          uploadDocument(fileName, oldDescription, "update-content-" + UUID.randomUUID());
      String documentId = JsonPath.read(uploadResponse, "$.document_id");
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      DocumentUpdateInput updateInput = new DocumentUpdateInput();
      updateInput.setDescription(newDescription);
      updateInput.setTagIds(List.of());
      updateInput.setExerciseIds(List.of());
      updateInput.setScenarioIds(List.of());

      // Act
      mvc.perform(
              put(DOCUMENT_API + "/{documentId}", documentId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      String newContent =
          assertAuditLogContainsNewContent(
              sizeBefore,
              "\"event_scope\" : \"update\"",
              "\"method\" : \"PUT\"",
              "\"url\" : \"http://localhost/api/documents/" + documentId + "\"",
              "\"input\" : {",
              "\"document_description\" : \"" + newDescription + "\"");
      assertThat(newContent)
          .doesNotContain("\"old_value\"")
          .doesNotContain("\"new_value\"")
          .contains("\"message\" : \"updates Document `" + documentId + "`\"");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_DOCUMENTS, Capability.DELETE_DOCUMENTS})
    void given_documentDeletion_should_logEntityTypeAndDocumentIdInMessage() throws Exception {
      // Arrange
      String fileName = "audit-delete-" + UUID.randomUUID() + ".txt";
      String uploadResponse =
          uploadDocument(fileName, "Delete description", "delete-content-" + UUID.randomUUID());
      String documentId = JsonPath.read(uploadResponse, "$.document_id");
      String documentName = JsonPath.read(uploadResponse, "$.document_name");
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      mvc.perform(delete(DOCUMENT_API + "/{documentId}", documentId).with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertAuditLogContainsNewContent(
          sizeBefore,
          "\"event_scope\" : \"delete\"",
          "\"entity_type\" : \"Document\"",
          "\"message\" : \"deletes Document `" + documentId + "`\"");
      assertThat(documentName).isNotBlank();
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_DOCUMENTS})
    void given_noOpDocumentUpdate_should_stillLogUpdateEvent() throws Exception {
      // Arrange
      String fileName = "audit-noop-" + UUID.randomUUID() + ".txt";
      String description = "No-op description";
      String uploadResponse =
          uploadDocument(fileName, description, "noop-content-" + UUID.randomUUID());
      String documentId = JsonPath.read(uploadResponse, "$.document_id");
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      DocumentUpdateInput updateInput = new DocumentUpdateInput();
      updateInput.setDescription(description);
      updateInput.setTagIds(List.of());
      updateInput.setExerciseIds(List.of());
      updateInput.setScenarioIds(List.of());

      // Act
      mvc.perform(
              put(DOCUMENT_API + "/{documentId}", documentId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertAuditLogContainsNewContent(
          sizeBefore,
          "\"event_scope\" : \"update\"",
          "\"method\" : \"PUT\"",
          "\"url\" : \"http://localhost/api/documents/" + documentId + "\"",
          "\"document_description\" : \"" + description + "\"");
    }

    private String uploadDocument(String fileName, String description, String content)
        throws Exception {
      DocumentCreateInput input = new DocumentCreateInput();
      input.setDescription(description);

      MockPart inputPart =
          new MockPart("input", asJsonString(input).getBytes(StandardCharsets.UTF_8));
      inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

      MockMultipartFile filePart =
          new MockMultipartFile(
              "file",
              fileName,
              MediaType.TEXT_PLAIN_VALUE,
              content.getBytes(StandardCharsets.UTF_8));

      return mvc.perform(
              multipart(DOCUMENT_API)
                  .part(inputPart)
                  .file(filePart)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }
  }

  private String assertAuditLogContainsNewContent(long sizeBefore, String... expectedSnippets) {
    return AuditLogTestHelper.assertAuditLogContainsNewContent(
        AUDIT_LOG_FILE, sizeBefore, expectedSnippets);
  }
}
