package io.openaev.aop.audit_log;

import static io.openaev.rest.role.TenantRoleApi.ROLE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(
    properties = {
      "openaev.audit-logs.transports=file",
      "openaev.audit-logs.halt-on-failure=false",
      "AUDIT_LOG_DIR=target/test-audit-log-role"
    })
@DisplayName("Role audit logging integration tests")
class AuditLoggerRoleTest extends IntegrationTest {

  private static final Path AUDIT_LOG_FILE = Paths.get("target/test-audit-log-role/audit.log");
  private static final String TEST_APPENDER_NAME = "AUDIT_ROLE_LOG_TEST_APPENDER";

  @Autowired private MockMvc mvc;
  @Autowired private AuditLogger auditLogger;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private PlatformSettingsService platformSettingsService;

  @BeforeAll
  void setupAuditFileAppender() throws Exception {
    AuditLogTestHelper.setupFileAppender(AUDIT_LOG_FILE, TEST_APPENDER_NAME);
  }

  @AfterAll
  void teardownAuditFileAppender() {
    AuditLogTestHelper.teardownFileAppender(TEST_APPENDER_NAME);
  }

  @BeforeEach
  void enableAuditLogger() throws Exception {
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
  @DisplayName("Admin role management")
  class AdminRoleManagement {

    @Test
    @WithMockUser(
        withCapabilities = {
          Capability.MANAGE_TENANT_SETTINGS,
          Capability.MANAGE_ASSESSMENT,
          Capability.MANAGE_ASSETS
        })
    void given_roleCapabilityLifecycle_should_logAdministrationEventsWithReadableCapabilityDiff()
        throws Exception {
      // -- ARRANGE --
      String roleName = "audit-role-" + UUID.randomUUID();
      RoleInput createInput =
          RoleInput.builder()
              .name(roleName)
              .description("role for audit integration test")
              .capabilities(Set.of(Capability.ACCESS_ASSESSMENT))
              .build();

      // -- ACT --
      String createResponse =
          mvc.perform(
                  post(ROLE_URI)
                      .content(asJsonString(createInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ARRANGE --
      String roleId = JsonPath.read(createResponse, "$.role_id");
      RoleInput assignCapabilitiesInput =
          RoleInput.builder()
              .name(roleName)
              .description("role for audit integration test")
              .capabilities(Set.of(Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSETS))
              .build();

      // -- ACT --
      long firstUpdateSizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;
      mvc.perform(
              put(ROLE_URI + "/" + roleId)
                  .content(asJsonString(assignCapabilitiesInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      String firstUpdateLog =
          readNewAuditLogContent(
              firstUpdateSizeBefore, "\"event_scope\" : \"update\"", "\"role_capabilities\"");
      assertThat(firstUpdateLog).contains("\"event_scope\" : \"update\"");
      assertThat(firstUpdateLog).contains("\"event_access\" : \"administration\"");
      assertThat(firstUpdateLog).contains("\"role_capabilities\"");
      assertThat(firstUpdateLog).contains("ACCESS_ASSESSMENT");
      assertThat(firstUpdateLog).contains("MANAGE_ASSETS");

      // Validate human-readable capability names are logged (not ordinals).
      assertThat(firstUpdateLog)
          .doesNotContainPattern("\\\"role_capabilities\\\"\\s*:\\s*\\[\\s*\\d");
      assertThat(firstUpdateLog).contains("ACCESS_ASSESSMENT");
      assertThat(firstUpdateLog).contains("MANAGE_ASSETS");

      // -- ARRANGE --
      RoleInput updateCapabilitiesInput =
          RoleInput.builder()
              .name(roleName)
              .description("role for audit integration test")
              .capabilities(Set.of(Capability.MANAGE_ASSESSMENT, Capability.MANAGE_ASSETS))
              .build();

      // -- ACT --
      long secondUpdateSizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;
      mvc.perform(
              put(ROLE_URI + "/" + roleId)
                  .content(asJsonString(updateCapabilitiesInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      String secondUpdateLog =
          readNewAuditLogContent(
              secondUpdateSizeBefore, "\"event_scope\" : \"update\"", "\"role_capabilities\"");
      assertThat(secondUpdateLog).contains("\"event_scope\" : \"update\"");
      assertThat(secondUpdateLog).contains("\"event_access\" : \"administration\"");
      assertThat(secondUpdateLog).contains("\"role_capabilities\"");
      assertThat(secondUpdateLog).contains("MANAGE_ASSESSMENT");
      assertThat(secondUpdateLog).contains("MANAGE_ASSETS");

      // Validate human-readable capability names are logged (not ordinals).
      assertThat(secondUpdateLog)
          .doesNotContainPattern("\\\"role_capabilities\\\"\\s*:\\s*\\[\\s*\\d");

      // Validate add/remove behavior across sequential updates.
      assertThat(firstUpdateLog).contains("ACCESS_ASSESSMENT");
      String secondUpdateInputCapabilities = extractInputCapabilitiesBlock(secondUpdateLog);
      assertThat(secondUpdateInputCapabilities).doesNotContain("ACCESS_ASSESSMENT");
      assertThat(secondUpdateInputCapabilities).contains("MANAGE_ASSESSMENT");

      assertThat(secondUpdateLog).doesNotContainPattern("\\\"old_value\\\"\\s*:\\s*\\[\\s*\\d");
      assertThat(secondUpdateLog).doesNotContainPattern("\\\"new_value\\\"\\s*:\\s*\\[\\s*\\d");
    }

    @Test
    @WithMockUser(
        withCapabilities = {
          Capability.MANAGE_TENANT_SETTINGS,
          Capability.ACCESS_ASSESSMENT,
          Capability.ACCESS_TAGS,
          Capability.MANAGE_TAGS,
          Capability.DELETE_TAGS
        })
    @DisplayName("Given role capability assignment, should audit tags capabilities update")
    void given_roleCapabilityAssignment_should_logTagsCapabilitiesInAuditLog() throws Exception {
      // -- ARRANGE --
      String roleName = "audit-tags-role-" + UUID.randomUUID();
      RoleInput createInput =
          RoleInput.builder()
              .name(roleName)
              .description("role for tags capability audit test")
              .capabilities(Set.of(Capability.ACCESS_TAGS))
              .build();

      String createResponse =
          mvc.perform(
                  post(ROLE_URI)
                      .content(asJsonString(createInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String roleId = JsonPath.read(createResponse, "$.role_id");
      RoleInput assignTagsCapabilitiesInput =
          RoleInput.builder()
              .name(roleName)
              .description("role for tags capability audit test")
              .capabilities(
                  Set.of(Capability.ACCESS_TAGS, Capability.MANAGE_TAGS, Capability.DELETE_TAGS))
              .build();

      // -- ACT --
      long updateSizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;
      mvc.perform(
              put(ROLE_URI + "/" + roleId)
                  .content(asJsonString(assignTagsCapabilitiesInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      String updateLog = readNewAuditLogContent(updateSizeBefore);
      assertThat(updateLog).contains("\"event_scope\" : \"update\"");
      assertThat(updateLog).contains("\"event_access\" : \"administration\"");
      assertThat(updateLog).contains("\"role_capabilities\"");
      assertThat(updateLog).contains("ACCESS_TAGS");
      assertThat(updateLog).contains("MANAGE_TAGS");
      assertThat(updateLog).contains("DELETE_TAGS");
      assertThat(updateLog).doesNotContainPattern("\\\"role_capabilities\\\"\\s*:\\s*\\[\\s*\\d");
    }
  }

  private String extractInputCapabilitiesBlock(String logEntry) {
    Pattern inputCapabilitiesPattern =
        Pattern.compile(
            "\"input\"\\s*:\\s*\\{[\\s\\S]*?\"role_capabilities\"\\s*:\\s*\\[(.*?)]",
            Pattern.DOTALL);
    Matcher matcher = inputCapabilitiesPattern.matcher(logEntry);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private String readNewAuditLogContent(long sizeBefore, String... expectedSnippets) {
    return AuditLogTestHelper.assertAuditLogContainsNewContent(
        AUDIT_LOG_FILE, sizeBefore, expectedSnippets);
  }
}
