package io.openaev.aop.audit_log;

import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
      "AUDIT_LOG_DIR=target/test-audit-logger"
    })
class AuditLoggerTest extends IntegrationTest {

  static {
    System.setProperty("AUDIT_LOG_DIR", "target/test-audit-logger");
  }

  private static final Path AUDIT_LOG_FILE = Paths.get("target/test-audit-logger/audit.log");
  private static final String TEST_APPENDER_NAME = "AUDIT_LOG_TEST_APPENDER";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamRepository teamRepository;
  @Autowired private AuditLogger auditLogger;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private PreviewFeatureService previewFeatureService;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("openaev.audit-logs.transports", () -> "file");
    registry.add("openaev.audit-logs.halt-on-failure", () -> "false");
  }

  @BeforeAll
  void setupAuditFileAppender() throws Exception {
    Files.createDirectories(AUDIT_LOG_FILE.getParent());
    Files.deleteIfExists(AUDIT_LOG_FILE);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger auditLogger = context.getLogger("AUDIT_LOG");

    if (auditLogger.getAppender(TEST_APPENDER_NAME) == null) {
      PatternLayoutEncoder encoder = new PatternLayoutEncoder();
      encoder.setContext(context);
      encoder.setPattern("%msg%n");
      encoder.start();

      FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new FileAppender<>();
      appender.setName(TEST_APPENDER_NAME);
      appender.setContext(context);
      appender.setFile(AUDIT_LOG_FILE.toString());
      appender.setAppend(true);
      appender.setEncoder(encoder);
      appender.start();

      auditLogger.addAppender(appender);
      auditLogger.setAdditive(false);
    }
  }

  @AfterAll
  void teardownAuditFileAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger auditLogger = context.getLogger("AUDIT_LOG");
    if (auditLogger.getAppender(TEST_APPENDER_NAME) != null) {
      auditLogger.detachAppender(TEST_APPENDER_NAME);
    }
  }

  @BeforeEach
  void enableAuditFeatureFlags() {
    Mockito.when(enterpriseEditionService.isLicenseActive(Mockito.any())).thenReturn(true);
    Mockito.when(previewFeatureService.isFeatureEnabled(PreviewFeature.AUDIT_LOG)).thenReturn(true);
    assertThat(auditLogger.isAuditLoggingEnabled()).isTrue();
  }

  @Nested
  @DisplayName("Login endpoint")
  class LoginEndpoint {

    @Test
    @DisplayName("Given login request should create audit file and log login event")
    void given_loginRequest_should_createAuditFileAndLogLoginEvent() throws Exception {
      // Arrange
      ensureLoginUserExists();
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;
      LoginUserInput loginUserInput = UserFixture.getLoginUserInput();

      // Act
      mvc.perform(
              post("/api/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(loginUserInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertAuditLogContainsNewContent(
          sizeBefore,
          "\"event_scope\" : \"login\"",
          "\"message\" : \"login from provider `local`\"");
    }
  }

  @Nested
  @DisplayName("Logout endpoint")
  class LogoutEndpoint {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("Given security logout request should append logout audit event")
    void given_securityLogoutRequest_should_appendLogoutAuditEvent() throws Exception {
      // Arrange
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      mvc.perform(post("/logout").accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().is3xxRedirection());

      // Assert
      assertAuditLogContainsNewContent(
          sizeBefore, "\"event_scope\" : \"logout\"", "\"message\" : \"logout\"");
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "Given explicit logout should log logout event and not produce spurious session_expired")
    void given_explicitLogout_should_logLogoutEvent_and_not_logSessionExpired() throws Exception {
      // Arrange
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      mvc.perform(post("/logout").accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().is3xxRedirection());

      // Assert — logout trace must be present (proves the event was emitted)
      assertAuditLogContainsNewContent(
          sizeBefore, "\"event_scope\" : \"logout\"", "\"message\" : \"logout\"");

      // Assert — no spurious session_expired must appear alongside the logout
      assertAuditLogDoesNotContainNewContent(sizeBefore, "session_expired");
    }
  }

  @Nested
  @DisplayName("Update endpoint")
  class UpdateEndpoint {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    @DisplayName("Given team update should append mutation update audit event")
    void given_teamUpdate_should_appendMutationUpdateAuditEvent() throws Exception {
      // Arrange
      Team team = teamRepository.saveAndFlush(TeamFixture.getDefaultTeam());
      TeamUpdateInput updateInput = new TeamUpdateInput();
      String updatedName = "AuditLoggerTest-" + UUID.randomUUID();
      updateInput.setName(updatedName);
      updateInput.setDescription("Updated by audit logger test");
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      mvc.perform(
              put(TEAM_URI + "/{teamId}", team.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertAuditLogContainsNewContent(
          sizeBefore,
          "\"event_scope\" : \"update\"",
          "\"event_type\" : \"mutation\"",
          "\"entity_type\" : \"Team\"");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    @DisplayName("Given authenticated request should populate session_id in audit log")
    void given_authenticatedRequest_should_populateSessionIdInAuditLog() throws Exception {
      // Arrange
      Team team = teamRepository.saveAndFlush(TeamFixture.getDefaultTeam());
      TeamUpdateInput updateInput = new TeamUpdateInput();
      updateInput.setName("SessionIdTest-" + UUID.randomUUID());
      updateInput.setDescription("Session ID verification test");
      long sizeBefore = Files.exists(AUDIT_LOG_FILE) ? Files.size(AUDIT_LOG_FILE) : 0L;

      // Act
      mvc.perform(
              put(TEAM_URI + "/{teamId}", team.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert — session_id must be present and non-null in the emitted trace
      assertAuditLogContainsNewContent(sizeBefore, "\"session_id\" :");
    }
  }

  private void ensureLoginUserExists() {
    User user =
        userRepository
            .findByEmailIgnoreCase(UserFixture.EMAIL)
            .orElseGet(
                () -> {
                  User created = UserFixture.getUser("Test", "User", UserFixture.EMAIL);
                  created.setPassword(UserFixture.ENCODED_PASSWORD);
                  return userComposer.forUser(created).persist().get();
                });

    // Keep a valid known password in case another test changed it.
    user.setPassword(UserFixture.ENCODED_PASSWORD);
    userRepository.saveAndFlush(user);
  }

  private void assertAuditLogContainsNewContent(long sizeBefore, String... expectedSnippets) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(Files.exists(AUDIT_LOG_FILE)).isTrue();
              long sizeAfter = Files.size(AUDIT_LOG_FILE);
              assertThat(sizeAfter).isGreaterThan(sizeBefore);

              String fullContent = Files.readString(AUDIT_LOG_FILE, StandardCharsets.UTF_8);
              String newContent =
                  fullContent.substring((int) Math.min(sizeBefore, fullContent.length()));

              for (String expectedSnippet : expectedSnippets) {
                assertThat(newContent).contains(expectedSnippet);
              }
            });
  }

  /**
   * Asserts that no new content matching {@code unexpectedSnippet} was written to the audit log
   * after position {@code sizeBefore}. Called after {@link #assertAuditLogContainsNewContent} has
   * already confirmed that the expected event was flushed, so any spurious sibling event would
   * already be present at this point.
   */
  private void assertAuditLogDoesNotContainNewContent(long sizeBefore, String unexpectedSnippet)
      throws Exception {
    if (!Files.exists(AUDIT_LOG_FILE)) {
      return;
    }
    String fullContent = Files.readString(AUDIT_LOG_FILE, StandardCharsets.UTF_8);
    String newContent = fullContent.substring((int) Math.min(sizeBefore, fullContent.length()));
    assertThat(newContent).doesNotContain(unexpectedSnippet);
  }
}
