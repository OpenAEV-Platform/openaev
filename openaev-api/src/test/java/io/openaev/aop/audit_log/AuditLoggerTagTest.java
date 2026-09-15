package io.openaev.aop.audit_log;

import static io.openaev.aop.audit_log.AuditLogTestHelper.setupFileAppender;
import static io.openaev.aop.audit_log.AuditLogTestHelper.teardownFileAppender;
import static io.openaev.rest.tag.TagApi.TAG_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tag;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Exercises the audit-log CRUD assertions against {@code TagApi} endpoints. */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(
    properties = {
      "openaev.audit-logs.transports=file",
      "openaev.audit-logs.halt-on-failure=false",
      "AUDIT_LOG_DIR=target/test-audit-log-tag"
    })
class AuditLoggerTagTest extends IntegrationTest {

  private static final Path AUDIT_LOG_FILE = Paths.get("target/test-audit-log-tag/audit.log");
  private static final String TEST_APPENDER_NAME = "AUDIT_LOG_TAG_E2E_TEST_APPENDER";

  @Autowired private MockMvc mvc;
  @Autowired private AuditLogger auditLogger;
  @Autowired private TagComposer tagComposer;

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
    tagComposer.reset();
    Files.writeString(
        AUDIT_LOG_FILE,
        "",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
  }

  @Nested
  @DisplayName("Tag lifecycle")
  class TagLifecycleAudit {

    @Test
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_TAGS},
        autoJoinDefaultTenant = true)
    void given_newTagCreation_should_logUpdateScope() throws Exception {
      // Arrange
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;
      TagCreateInput input = new TagCreateInput();
      input.setName("audit-create-" + UUID.randomUUID());
      input.setColor("#FFFFFF");

      // Act
      mvc.perform(
              post(TAG_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      String newContent =
          assertAuditLogContainsNewContent(
              sizeBefore,
              "\"event_scope\" : \"update\"",
              "\"method\" : \"POST\"",
              "\"url\" : \"http://localhost/api/tags\"",
              "\"entity_type\" : \"Tag\"");
      assertThat(newContent).contains("\"message\" : \"updates Tag\"");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS})
    void given_tagMetadataUpdate_should_notLogChangedInputField() throws Exception {
      // Arrange
      String oldName = "audit-update-" + UUID.randomUUID();
      String newName = "audit-updated-" + UUID.randomUUID();
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText(oldName)).persist().get();
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      TagUpdateInput updateInput = new TagUpdateInput();
      updateInput.setName(newName);
      updateInput.setColor(tag.getColor());

      // Act
      mvc.perform(
              put(TAG_URI + "/{tagId}", tag.getId())
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
              "\"url\" : \"http://localhost/api/tags/" + tag.getId() + "\"",
              "\"input\" : {",
              "\"tag_name\" : \"" + newName + "\"");
      assertThat(newContent)
          .doesNotContain("\"old_value\"")
          .doesNotContain("\"new_value\"")
          .contains("\"message\" : \"updates Tag `" + tag.getId() + "`\"");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS})
    void given_noOpTagUpdate_should_stillLogUpdateEvent() throws Exception {
      // Arrange
      String name = "audit-noop-" + UUID.randomUUID();
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText(name)).persist().get();
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      TagUpdateInput updateInput = new TagUpdateInput();
      updateInput.setName(name);
      updateInput.setColor(tag.getColor());

      // Act
      mvc.perform(
              put(TAG_URI + "/{tagId}", tag.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertAuditLogContainsNewContent(
          sizeBefore,
          "\"event_scope\" : \"update\"",
          "\"method\" : \"PUT\"",
          "\"url\" : \"http://localhost/api/tags/" + tag.getId() + "\"",
          "\"tag_name\" : \"" + name + "\"");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS, Capability.DELETE_TAGS})
    void given_tagDeletion_should_notAppendAuditEvent() throws Exception {
      // Arrange
      Tag tag =
          tagComposer
              .forTag(TagFixture.getTagWithText("audit-delete-" + UUID.randomUUID()))
              .persist()
              .get();
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      mvc.perform(delete(TAG_URI + "/{tagId}", tag.getId()).with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertNoAuditLogAppendWithin(sizeBefore, 1);
    }
  }

  private String assertAuditLogContainsNewContent(long sizeBefore, String... expectedSnippets) {
    return AuditLogTestHelper.assertAuditLogContainsNewContent(
        AUDIT_LOG_FILE, sizeBefore, expectedSnippets);
  }

  private void assertNoAuditLogAppendWithin(long sizeBefore, long durationSeconds) {
    Awaitility.await()
        .during(durationSeconds, TimeUnit.SECONDS)
        .atMost(durationSeconds + 1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              long sizeAfter = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;
              assertThat(sizeAfter).isEqualTo(sizeBefore);
            });
  }
}
