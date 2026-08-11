package io.openaev.rest.finding;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.openaev.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ShutdownService;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingComment;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingCommentRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.LogService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual verification of the 3 scenarios requested for the FindingComment RBAC feature: (a)
 * MANAGE_FINDINGS creates a comment -> 200, (b) MANAGE_FINDINGS holder edits ANOTHER user's comment
 * -> 403 (author-only ownership check), (c) DELETE_FINDINGS holder (not the author) deletes a
 * comment -> success, and the audit log captures the full original content.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class FindingCommentApiTest extends IntegrationTest {

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private FindingCommentRepository findingCommentRepository;
  @Autowired private FindingRepository findingRepository;
  @Autowired private EntityManager entityManager;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private AuditLogProperties auditLogProperties;
  @MockitoSpyBean private LogService logService;
  @MockitoSpyBean private ShutdownService shutdownService;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setUp() {
    reset(auditLogger, auditLogProperties, logService, shutdownService);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doNothing().when(shutdownService).initiateShutdown();
    findingComposer.reset();
    injectComposer.reset();
    userComposer.reset();
  }

  private Finding createFinding() {
    InjectComposer.Composer injectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
    return findingComposer
        .forFinding(createDefaultTextFindingWithRandomValue())
        .withInject(injectWrapper)
        .persist()
        .get();
  }

  /**
   * Creates a second, distinct user (not the mock test user) and makes them the current principal
   * just long enough to persist a comment authored by them, then restores the original test user.
   * Mirrors TestUserHolder#set/#refreshSecurityContext used by WithMockUserTestExecutionListener.
   */
  private FindingComment createCommentByOtherUser(Finding finding, String content) {
    User originalUser = testUserHolder.get();
    User otherUser =
        userComposer
            .forUser(
                UserFixture.getUser(
                    "Other", "Author", UUID.randomUUID() + "@unittests.invalid", false))
            .persist()
            .get();

    testUserHolder.set(otherUser);
    testUserHolder.refreshSecurityContext();

    FindingComment comment = new FindingComment();
    comment.setFinding(finding);
    comment.setAuthor(otherUser);
    comment.setContent(content);
    comment = findingCommentRepository.save(comment);
    entityManager.flush();
    entityManager.clear();

    testUserHolder.set(originalUser);
    testUserHolder.refreshSecurityContext();
    return comment;
  }

  @Test
  @DisplayName("(a) MANAGE_FINDINGS creates a comment -> 200 OK")
  @WithMockUser(withCapabilities = {Capability.MANAGE_FINDINGS})
  void given_userWithManageFindings_when_creatingComment_should_return200() throws Exception {
    Finding finding = createFinding();
    JsonNode body = instance.objectNode().put("finding_comment_content", "hello finding");

    mvc.perform(
            post("/api/findings/{id}/comments", finding.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Adding a comment bumps finding_human_updated_at (\"Updated at\") but leaves"
          + " finding_updated_at (\"Last seen\") untouched")
  @WithMockUser(withCapabilities = {Capability.MANAGE_FINDINGS})
  void given_finding_when_addingComment_should_bumpHumanUpdateDateOnlyNotUpdateDate()
      throws Exception {
    Finding finding = createFinding();
    // Force the pending INSERT to flush now: @UpdateTimestamp's real generated value is only
    // written back to the entity at flush time, which the composer's persist() may defer -
    // reading it too early would capture the field's construction-time placeholder instead.
    entityManager.flush();
    Instant updateDateBeforeComment = finding.getUpdateDate();
    assertThat(finding.getHumanUpdateDate()).isNull();
    JsonNode body = instance.objectNode().put("finding_comment_content", "hello finding");

    mvc.perform(
            post("/api/findings/{id}/comments", finding.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk());

    // touchHumanUpdate is a native bulk update, invisible to the 1st-level cache - clear it so
    // this re-fetch actually hits the DB instead of returning the stale in-memory `finding`.
    entityManager.clear();
    Finding persisted = findingRepository.findById(finding.getId()).orElseThrow();
    assertThat(persisted.getHumanUpdateDate()).isNotNull();
    assertThat(persisted.getUpdateDate()).isEqualTo(updateDateBeforeComment);
  }

  @Test
  @DisplayName("(b) MANAGE_FINDINGS holder editing another user's comment -> 403 Forbidden")
  @WithMockUser(withCapabilities = {Capability.MANAGE_FINDINGS})
  void given_commentAuthoredByAnotherUser_when_editing_should_return403() throws Exception {
    Finding finding = createFinding();
    FindingComment comment = createCommentByOtherUser(finding, "original content");

    JsonNode body = instance.objectNode().put("finding_comment_content", "edited content");

    mvc.perform(
            put("/api/findings/comments/{commentId}", comment.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "(c) DELETE_FINDINGS holder deletes another user's comment -> success, "
          + "audit log preserves full original content")
  @WithMockUser(withCapabilities = {Capability.DELETE_FINDINGS})
  void
      given_userWithDeleteFindings_when_deletingAnotherUsersComment_should_succeedAndAuditFullContent()
          throws Exception {
    Finding finding = createFinding();
    String originalContent = "sensitive original content that must survive deletion";
    FindingComment comment = createCommentByOtherUser(finding, originalContent);

    ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JsonNode> outputCaptor = ArgumentCaptor.forClass(JsonNode.class);

    mvc.perform(delete("/api/findings/comments/{commentId}", comment.getId()).with(csrf()))
        .andExpect(status().isOk());

    verify(auditLogger, timeout(2000))
        .logAccessControlEvent(
            eventScopeCaptor.capture(),
            anyString(),
            any(ResourceType.class),
            anyString(),
            any(),
            outputCaptor.capture(),
            any(),
            any(),
            anyString());

    assertThat(eventScopeCaptor.getValue()).isEqualTo("delete");
    assertThat(outputCaptor.getValue().toString()).contains(originalContent);
    assertThat(findingCommentRepository.findById(comment.getId())).isEmpty();
  }

  // Regression coverage for the bug where FindingCommentApi only registered the non-tenant URIs:
  // the frontend's Action.ts always calls through buildTenantApiPath, so every request in a real
  // browser session hits /api/tenants/{tenantId}/findings/... - if only the bare /api/findings/...
  // mapping exists, that request 404s (reported live as "Post" doing nothing in the Comment tab).

  @Test
  @DisplayName("GET comments via tenant-prefixed URI -> 200 OK")
  @WithMockUser(withCapabilities = {Capability.ACCESS_FINDINGS})
  void given_finding_when_gettingCommentsViaTenantUri_should_return200() throws Exception {
    Finding finding = createFinding();

    mvc.perform(
            get(tenantUri(FindingCommentApi.TENANT_FINDING_COMMENTS_URI), finding.getId())
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST comment via tenant-prefixed URI -> 200 OK")
  @WithMockUser(withCapabilities = {Capability.MANAGE_FINDINGS})
  void given_userWithManageFindings_when_creatingCommentViaTenantUri_should_return200()
      throws Exception {
    Finding finding = createFinding();
    JsonNode body = instance.objectNode().put("finding_comment_content", "hello finding");

    mvc.perform(
            post(tenantUri(FindingCommentApi.TENANT_FINDING_COMMENTS_URI), finding.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PUT comment via tenant-prefixed URI -> 200 OK")
  @WithMockUser(withCapabilities = {Capability.MANAGE_FINDINGS})
  void given_ownComment_when_editingViaTenantUri_should_return200() throws Exception {
    Finding finding = createFinding();
    JsonNode createBody = instance.objectNode().put("finding_comment_content", "original");
    String responseBody =
        mvc.perform(
                post("/api/findings/{id}/comments", finding.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(createBody)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String commentId = mapper.readTree(responseBody).get("finding_comment_id").asText();

    JsonNode updateBody = instance.objectNode().put("finding_comment_content", "edited");

    mvc.perform(
            put(tenantUri(FindingCommentApi.TENANT_FINDING_COMMENT_URI), commentId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateBody)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("DELETE comment via tenant-prefixed URI -> 200 OK")
  @WithMockUser(withCapabilities = {Capability.DELETE_FINDINGS})
  void given_comment_when_deletingViaTenantUri_should_return200() throws Exception {
    Finding finding = createFinding();
    FindingComment comment = createCommentByOtherUser(finding, "to be deleted");

    mvc.perform(
            delete(tenantUri(FindingCommentApi.TENANT_FINDING_COMMENT_URI), comment.getId())
                .with(csrf()))
        .andExpect(status().isOk());
  }
}
