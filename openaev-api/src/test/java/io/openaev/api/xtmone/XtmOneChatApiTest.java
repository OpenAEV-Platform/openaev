package io.openaev.api.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.openaev.IntegrationTest;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import io.openaev.xtmone.XtmOneUpstreamException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

@TestInstance(PER_CLASS)
@DisplayName("XTM One Chat API tests")
class XtmOneChatApiTest extends IntegrationTest {

  private static final String CHAT_AGENTS_URL = "/api/xtmone/chat/agents";
  private static final String CHAT_SESSIONS_URL = "/api/xtmone/chat/sessions";
  private static final String CHAT_MESSAGES_URL = "/api/xtmone/chat/messages";
  private static final String CHAT_STEER_URL = "/api/xtmone/chat/messages/steer";
  private static final String CHAT_APPROVE_URL = "/api/xtmone/chat/messages/approve";
  private static final String CHAT_PROMPTS_URL = "/api/xtmone/chat/prompts";
  private static final String CHAT_QUOTA_URL = "/api/xtmone/chat/quota";
  private static final String CHAT_WORKSPACES_URL = "/api/xtmone/chat/workspaces";
  private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";
  private static final String MESSAGE_ID = "22222222-2222-2222-2222-222222222222";
  private static final String WORKSPACE_ID = "33333333-3333-3333-3333-333333333333";
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mvc;
  @MockitoBean private XtmOneClient xtmOneClient;
  @MockitoBean private XtmOneConfig xtmOneConfig;

  // The new history/steering endpoints are EE-gated (@AccessControl(isEnterpriseEdition = true)).
  // The mock's isEnterpriseLicenseInactive() returns false by default, i.e. an active EE license.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Nested
  @DisplayName("XTM One not configured")
  class NotConfigured {

    private static Stream<Arguments> chatRoutes() {
      String conversationUrl = CHAT_SESSIONS_URL + "/" + CONVERSATION_ID;
      String workspaceUrl = CHAT_WORKSPACES_URL + "/" + WORKSPACE_ID;
      String conversationBody = "{\"conversation_id\":\"" + CONVERSATION_ID + "\"";
      return Stream.of(
          Arguments.of("GET agents", get(CHAT_AGENTS_URL)),
          Arguments.of("POST sessions", json(post(CHAT_SESSIONS_URL), "{\"agent_slug\":\"a\"}")),
          Arguments.of("GET sessions", get(CHAT_SESSIONS_URL)),
          Arguments.of("PATCH session", json(patch(conversationUrl), "{\"title\":\"Renamed\"}")),
          Arguments.of("DELETE session", delete(conversationUrl).with(csrf())),
          Arguments.of("GET workspaces", get(CHAT_WORKSPACES_URL)),
          Arguments.of(
              "POST workspaces", json(post(CHAT_WORKSPACES_URL), "{\"name\":\"Red team\"}")),
          Arguments.of("PATCH workspace", json(patch(workspaceUrl), "{\"name\":\"Blue\"}")),
          Arguments.of("DELETE workspace", delete(workspaceUrl).with(csrf())),
          Arguments.of(
              "POST steer",
              json(post(CHAT_STEER_URL), conversationBody + ",\"content\":\"hello\"}")),
          Arguments.of(
              "POST approve",
              json(
                  post(CHAT_APPROVE_URL),
                  conversationBody
                      + ",\"decisions\":[{\"tool_call_id\":\"t\",\"decision\":\"approve\"}]}")),
          Arguments.of(
              "GET pending approvals",
              get("/api/xtmone/chat/conversations/" + CONVERSATION_ID + "/pending-approvals")),
          Arguments.of("GET prompts", get(CHAT_PROMPTS_URL)),
          Arguments.of("GET quota", get(CHAT_QUOTA_URL)),
          Arguments.of(
              "POST feedback",
              json(post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)), "{\"rating\":\"positive\"}")),
          Arguments.of(
              "DELETE feedback", delete(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)).with(csrf())),
          Arguments.of(
              "POST messages (stream)",
              json(post(CHAT_MESSAGES_URL), "{\"content\":\"hello\"}")
                  .accept(MediaType.TEXT_EVENT_STREAM)),
          Arguments.of(
              "POST upload",
              multipart("/api/xtmone/chat/upload")
                  .file(new MockMultipartFile("file", "iocs.csv", "text/csv", new byte[] {1}))
                  .param("conversation_id", CONVERSATION_ID)
                  .with(csrf())),
          Arguments.of(
              "GET file download", get("/api/xtmone/chat/files/" + MESSAGE_ID + "/download")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chatRoutes")
    @WithMockUser
    @DisplayName("Given XTM One not configured should answer 503 with one message, on every route")
    void given_notConfigured_should_answerServiceUnavailable(String route, RequestBuilder request)
        throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(request)
          .andExpect(status().isServiceUnavailable())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.detail").value("XTM One is not configured"));
      verifyNoInteractions(xtmOneClient);
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/agents")
  class ListAgents {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One configured and returns agents should return 200 with agent list")
    void given_configured_should_returnAgentList() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.issueAuthenticationJwt(anyString(), anyString(), anyString()))
          .thenReturn("fake-jwt");
      List<ChatbotAgentOutput> agents =
          List.of(
              new ChatbotAgentOutput("agent-1", "Test Agent", "test-agent", "Test Agent"),
              new ChatbotAgentOutput("agent-2", "Another Agent", "agent-2", "another-agent"));
      when(xtmOneClient.listChatAgents(anyString())).thenReturn(agents);

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_AGENTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value("agent-1"))
          .andExpect(jsonPath("$[0].name").value("Test Agent"))
          .andExpect(jsonPath("$[1].id").value("agent-2"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One answers 503 should relay the status and XTM One's detail")
    void given_xtmOneReturns503_should_return503() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatAgents(anyString())).thenThrow(refusal(503, "Service unavailable"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_AGENTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value("Service unavailable"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One rejects OpenAEV's credentials should answer 422, never a 401")
    void given_xtmOneRejectsCredentials_should_return422() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatAgents(anyString())).thenThrow(refusal(422, "[XTM One] HTTP 401"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_AGENTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.detail").value("[XTM One] HTTP 401"));
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/files/{fileId}/download")
  class DownloadFile {

    private static final String VALID_FILE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String DOWNLOAD_URL =
        "/api/xtmone/chat/files/" + VALID_FILE_ID + "/download";

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID file id should return 400 without calling XTM One")
    void given_invalidFileId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(get("/api/xtmone/chat/files/not-a-uuid/download"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns a file should stream the bytes and headers")
    void given_configured_should_streamFile() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      byte[] data = "type,value\nip,1.2.3.4".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      when(xtmOneClient.downloadChatFile(VALID_FILE_ID))
          .thenReturn(
              new XtmOneClient.DownloadedFile(
                  data, "text/csv", "attachment; filename=\"iocs.csv\""));

      // -- ACT & ASSERT --
      mvc.perform(get(DOWNLOAD_URL))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Disposition", "attachment; filename=\"iocs.csv\""))
          .andExpect(content().bytes(data));
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns 503 should propagate 503 to client")
    void given_xtmOneReturns503_should_return503() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.downloadChatFile(VALID_FILE_ID))
          .thenThrow(
              new ResponseStatusException(
                  HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] File download failed"));

      // -- ACT & ASSERT --
      mvc.perform(get(DOWNLOAD_URL)).andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One refuses the download should relay the status and XTM One's detail")
    void given_xtmOneRefuses_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.downloadChatFile(VALID_FILE_ID)).thenThrow(refusal(404, "File not found"));

      // -- ACT & ASSERT --
      mvc.perform(get(DOWNLOAD_URL))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("File not found"));
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/sessions")
  class ListSessions {

    @Test
    @WithMockUser
    @DisplayName("Given upstream refusal should relay the status and XTM One's detail")
    void given_upstreamRefusal_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatSessions())
          .thenReturn(relayed(500, "{\"detail\":\"Database unavailable\"}"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_SESSIONS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.detail").value("Database unavailable"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream conversations should return the payload as-is")
    void given_conversations_should_returnPayload() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatSessions())
          .thenReturn(
              ok(
                  Map.of(
                      "conversations",
                      List.of(
                          Map.of(
                              "conversation_id", CONVERSATION_ID, "title", "My conversation")))));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_SESSIONS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversations.length()").value(1))
          .andExpect(jsonPath("$.conversations[0].conversation_id").value(CONVERSATION_ID))
          .andExpect(jsonPath("$.conversations[0].title").value("My conversation"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given conversations filed in a workspace should pass their workspace_id through")
    void given_conversationsInWorkspaces_should_passWorkspaceIdThrough() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatSessions())
          .thenReturn(
              ok(
                  Map.of(
                      "conversations",
                      List.of(
                          Map.of(
                              "conversation_id",
                              CONVERSATION_ID,
                              "title",
                              "Filed",
                              "workspace_id",
                              WORKSPACE_ID)))));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_SESSIONS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversations[0].workspace_id").value(WORKSPACE_ID));
    }
  }

  @Nested
  @DisplayName("POST /api/xtmone/chat/sessions")
  class CreateSession {

    @Test
    @WithMockUser
    @DisplayName("Given a workspace and a field unknown to OpenAEV should forward the body as-is")
    void given_workspaceAndUnknownField_should_forwardBodyAsIs() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.createChatSession(anyMap()))
          .thenReturn(ok(Map.of("conversation_id", CONVERSATION_ID, "workspace_id", WORKSPACE_ID)));

      // -- ACT --
      mvc.perform(
              post(CHAT_SESSIONS_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"agent_slug\":\"ariane\",\"workspace_id\":\""
                          + WORKSPACE_ID
                          + "\",\"future_field\":true}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.workspace_id").value(WORKSPACE_ID));

      // -- ASSERT --
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
      verify(xtmOneClient).createChatSession(captor.capture());
      assertThat(captor.getValue())
          .isEqualTo(
              Map.of("agent_slug", "ariane", "workspace_id", WORKSPACE_ID, "future_field", true));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream refusal should relay the status and XTM One's detail")
    void given_upstreamRefusal_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.createChatSession(anyMap()))
          .thenReturn(relayed(404, "{\"detail\":\"Conversation not found\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_SESSIONS_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"agent_slug\":\"ariane\"}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }
  }

  @Nested
  @DisplayName("PATCH /api/xtmone/chat/sessions/{conversationId}")
  class UpdateSession {

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID conversation id should return 400 without calling XTM One")
    void given_invalidConversationId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_SESSIONS_URL + "/not-a-uuid")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"Renamed\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID workspace id should return 400 without calling XTM One")
    void given_invalidWorkspaceId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"workspace_id\":\"../other\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a title that is not a string should return 400 without calling XTM One")
    void given_nonStringTitle_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":42}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a new title should forward it and relay the renamed conversation")
    void given_newTitle_should_forwardAndRelay() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.updateChatSession(CONVERSATION_ID, Map.of("title", "Renamed")))
          .thenReturn(
              relayed(
                  200,
                  "{\"conversation_id\":\""
                      + CONVERSATION_ID
                      + "\",\"title\":\"Renamed\",\"workspace_id\":null}"));

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"Renamed\",\"ignored\":true}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversation_id").value(CONVERSATION_ID))
          .andExpect(jsonPath("$.title").value("Renamed"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given a null workspace id should forward the explicit null that unfiles it")
    void given_nullWorkspaceId_should_forwardExplicitNull() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.updateChatSession(eq(CONVERSATION_ID), anyMap()))
          .thenReturn(relayed(200, "{\"conversation_id\":\"" + CONVERSATION_ID + "\"}"));

      // -- ACT --
      mvc.perform(
              patch(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"workspace_id\":null}"))
          .andExpect(status().isOk());

      // -- ASSERT --
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
      verify(xtmOneClient).updateChatSession(eq(CONVERSATION_ID), captor.capture());
      assertThat(captor.getValue()).hasSize(1).containsEntry("workspace_id", null);
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 404 should relay the status and XTM One's detail")
    void given_upstreamNotFound_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.updateChatSession(eq(CONVERSATION_ID), anyMap()))
          .thenReturn(relayed(404, "{\"detail\":\"Conversation not found\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"workspace_id\":\"" + WORKSPACE_ID + "\"}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/workspaces")
  class ListWorkspaces {

    @Test
    @WithMockUser
    @DisplayName("Given upstream workspaces should relay every workspace field unchanged")
    void given_workspaces_should_relayEveryField() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatWorkspaces())
          .thenReturn(
              relayed(
                  200,
                  "{\"workspaces\":[{\"id\":\""
                      + WORKSPACE_ID
                      + "\",\"name\":\"Red team\",\"is_default\":false,"
                      + "\"is_own\":true,\"can_manage\":true,\"is_company_managed\":false,"
                      + "\"user_id\":\"user-1\",\"owner_name\":\"Analyst\"}]}"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_WORKSPACES_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.workspaces[0].id").value(WORKSPACE_ID))
          .andExpect(jsonPath("$.workspaces[0].name").value("Red team"))
          .andExpect(jsonPath("$.workspaces[0].is_default").value(false))
          .andExpect(jsonPath("$.workspaces[0].is_own").value(true))
          .andExpect(jsonPath("$.workspaces[0].can_manage").value(true))
          .andExpect(jsonPath("$.workspaces[0].is_company_managed").value(false))
          .andExpect(jsonPath("$.workspaces[0].user_id").value("user-1"))
          .andExpect(jsonPath("$.workspaces[0].owner_name").value("Analyst"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not licensed (403) should relay the status and XTM One's detail")
    void given_upstreamForbidden_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatWorkspaces())
          .thenReturn(relayed(403, "{\"detail\":\"Enterprise Edition required\"}"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_WORKSPACES_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.detail").value("Enterprise Edition required"));
    }
  }

  @Nested
  @DisplayName("POST /api/xtmone/chat/workspaces")
  class CreateWorkspace {

    @Test
    @WithMockUser
    @DisplayName("Given no name should return 400 without calling XTM One")
    void given_noName_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_WORKSPACES_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"description\":\"Q3\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a name that is not a string should return 400 without calling XTM One")
    void given_nonStringName_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_WORKSPACES_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":[\"Red team\"]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a name should forward only the workspace fields and relay the 201")
    void given_name_should_forwardWorkspaceFieldsAndRelayCreated() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.createChatWorkspace(Map.of("name", "Red team", "description", "Q3")))
          .thenReturn(relayed(201, "{\"id\":\"" + WORKSPACE_ID + "\",\"name\":\"Red team\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_WORKSPACES_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"name\":\"Red team\",\"description\":\"Q3\","
                          + "\"is_company_managed\":true}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(WORKSPACE_ID))
          .andExpect(jsonPath("$.name").value("Red team"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 422 should relay the status and XTM One's detail")
    void given_upstreamUnprocessable_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.createChatWorkspace(anyMap()))
          .thenReturn(relayed(422, "{\"detail\":\"A workspace with this name already exists\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_WORKSPACES_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Red team\"}"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.detail").value("A workspace with this name already exists"));
    }
  }

  @Nested
  @DisplayName("PATCH /api/xtmone/chat/workspaces/{workspaceId}")
  class UpdateWorkspace {

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID workspace id should return 400 without calling XTM One")
    void given_invalidWorkspaceId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_WORKSPACES_URL + "/not-a-uuid")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Blue team\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a new name should forward it and relay the renamed workspace")
    void given_newName_should_forwardAndRelay() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.updateChatWorkspace(WORKSPACE_ID, Map.of("name", "Blue team")))
          .thenReturn(relayed(200, "{\"id\":\"" + WORKSPACE_ID + "\",\"name\":\"Blue team\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              patch(CHAT_WORKSPACES_URL + "/" + WORKSPACE_ID)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Blue team\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Blue team"));
    }
  }

  @Nested
  @DisplayName("DELETE /api/xtmone/chat/workspaces/{workspaceId}")
  class DeleteWorkspace {

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID workspace id should return 400 without calling XTM One")
    void given_invalidWorkspaceId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_WORKSPACES_URL + "/not-a-uuid").with(csrf()))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream deletes the workspace should return 204")
    void given_upstreamDeletes_should_returnNoContent() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.deleteChatWorkspace(WORKSPACE_ID))
          .thenReturn(new XtmOneClient.RelayedResponse(204, null));

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_WORKSPACES_URL + "/" + WORKSPACE_ID).with(csrf()))
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream refuses (409) should relay the status and XTM One's detail")
    void given_upstreamRefuses_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.deleteChatWorkspace(WORKSPACE_ID))
          .thenReturn(relayed(409, "{\"detail\":\"This workspace still holds work items\"}"));

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_WORKSPACES_URL + "/" + WORKSPACE_ID).with(csrf()))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value("This workspace still holds work items"));
    }
  }

  @Nested
  @DisplayName("DELETE /api/xtmone/chat/sessions/{conversationId}")
  class DeleteSession {

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID conversation id should return 400 without calling XTM One")
    void given_invalidConversationId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_SESSIONS_URL + "/not-a-uuid").with(csrf()))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream accepts the deletion should return 204")
    void given_upstreamAccepts_should_returnNoContent() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.deleteChatSession(CONVERSATION_ID))
          .thenReturn(new XtmOneClient.RelayedResponse(204, null));

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID).with(csrf()))
          .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream rejects the deletion should relay the status and detail")
    void given_upstreamRejects_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.deleteChatSession(CONVERSATION_ID))
          .thenReturn(relayed(404, "{\"detail\":\"Conversation not found\"}"));

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID).with(csrf()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }
  }

  @Nested
  @DisplayName("POST /api/xtmone/chat/messages/steer")
  class SteerMessage {

    @Test
    @WithMockUser
    @DisplayName("Given a blank content should return 400 without calling XTM One")
    void given_blankContent_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_STEER_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\" \",\"conversation_id\":\"" + CONVERSATION_ID + "\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID conversation id should return 400 without calling XTM One")
    void given_invalidConversationId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_STEER_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"hello\",\"conversation_id\":\"not-a-uuid\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream accepts the steering should return 200 with the payload")
    void given_upstreamAccepts_should_returnPayload() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.steerChatMessage("hello", CONVERSATION_ID))
          .thenReturn(ok(Map.of("status", "queued")));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_STEER_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"content\":\"hello\",\"conversation_id\":\"" + CONVERSATION_ID + "\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("queued"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 409 (no run active) should relay 409 and its detail")
    void given_upstreamConflict_should_return409() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.steerChatMessage("hello", CONVERSATION_ID))
          .thenReturn(relayed(409, "{\"detail\":\"No response is currently being generated\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_STEER_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"content\":\"hello\",\"conversation_id\":\"" + CONVERSATION_ID + "\"}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value("No response is currently being generated"));
    }
  }

  @Nested
  @DisplayName("POST /api/xtmone/chat/messages/approve")
  class ApproveToolCalls {

    private static final String DECISION =
        "{\"tool_call_id\":\"toolu_1\",\"decision\":\"approve\"}";

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID conversation id should return 400 without calling XTM One")
    void given_invalidConversationId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"conversation_id\":\"not-a-uuid\",\"decisions\":[" + DECISION + "]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given an empty decisions array should return 400 without calling XTM One")
    void given_emptyDecisions_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"conversation_id\":\"" + CONVERSATION_ID + "\",\"decisions\":[]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given no decisions field at all should return 400 without calling XTM One")
    void given_noDecisionsField_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"conversation_id\":\"" + CONVERSATION_ID + "\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a decision without a tool_call_id should return 400")
    void given_decisionWithoutToolCallId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"decision\":\"approve\"}]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given an unknown verdict should return 400 without calling XTM One")
    void given_unknownVerdict_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"tool_call_id\":\"toolu_1\","
                          + "\"decision\":\"yes\"}]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a differently-cased verdict should return 400")
    void given_miscasedVerdict_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"tool_call_id\":\"toolu_1\","
                          + "\"decision\":\"Approve\"}]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given more decisions than the cap should return 400 without calling XTM One")
    void given_tooManyDecisions_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      StringBuilder payload =
          new StringBuilder("{\"conversation_id\":\"" + CONVERSATION_ID + "\",\"decisions\":[");
      for (int i = 0; i < 51; i++) {
        if (i > 0) {
          payload.append(',');
        }
        payload
            .append("{\"tool_call_id\":\"toolu_")
            .append(i)
            .append("\",\"decision\":\"approve\"}");
      }
      payload.append("]}");

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload.toString()))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given an over-long rejection reason should return 400 without calling XTM One")
    void given_overLongRejectionReason_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      String reason = "x".repeat(2001);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"tool_call_id\":\"toolu_1\","
                          + "\"decision\":\"reject\",\"rejection_reason\":\""
                          + reason
                          + "\"}]}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a rejection with a reason should forward the reason upstream")
    void given_rejectionWithReason_should_forwardReason() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.approveToolCalls(anyString(), anyList()))
          .thenReturn(ok(Map.of("status", "accepted", "decided", 1)));

      // -- ACT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"tool_call_id\":\"toolu_1\","
                          + "\"decision\":\"reject\",\"rejection_reason\":\"too broad\"}]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("accepted"));

      // -- ASSERT --
      ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.captor();
      verify(xtmOneClient).approveToolCalls(eq(CONVERSATION_ID), captor.capture());
      assertThat(captor.getValue())
          .singleElement()
          .isEqualTo(
              Map.of(
                  "tool_call_id",
                  "toolu_1",
                  "decision",
                  "reject",
                  "rejection_reason",
                  "too broad"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given an approval carrying a reason should drop the reason and still approve")
    void given_approvalWithReason_should_dropReason() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.approveToolCalls(anyString(), anyList()))
          .thenReturn(ok(Map.of("status", "accepted")));

      // -- ACT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"tool_call_id\":\"toolu_1\","
                          + "\"decision\":\"approve\",\"rejection_reason\":\"stray note\"}]}"))
          .andExpect(status().isOk());

      // -- ASSERT --
      ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.captor();
      verify(xtmOneClient).approveToolCalls(eq(CONVERSATION_ID), captor.capture());
      assertThat(captor.getValue())
          .singleElement()
          .isEqualTo(Map.of("tool_call_id", "toolu_1", "decision", "approve"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given an approve_always verdict should forward it as-is")
    void given_approveAlways_should_forwardVerdict() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.approveToolCalls(anyString(), anyList()))
          .thenReturn(ok(Map.of("status", "accepted")));

      // -- ACT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":[{\"tool_call_id\":\"toolu_1\","
                          + "\"decision\":\"approve_always\"}]}"))
          .andExpect(status().isOk());

      // -- ASSERT --
      ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.captor();
      verify(xtmOneClient).approveToolCalls(eq(CONVERSATION_ID), captor.capture());
      assertThat(captor.getValue().getFirst())
          .containsEntry("decision", "approve_always")
          .doesNotContainKey("rejection_reason");
    }

    @Test
    @WithMockUser
    @DisplayName("Given several proposals should forward every decision in order")
    void given_severalDecisions_should_forwardAll() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.approveToolCalls(anyString(), anyList()))
          .thenReturn(ok(Map.of("status", "accepted", "decided", 2)));

      // -- ACT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":["
                          + "{\"tool_call_id\":\"toolu_1\",\"decision\":\"approve\"},"
                          + "{\"tool_call_id\":\"toolu_2\",\"decision\":\"reject\"}]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.decided").value(2));

      // -- ASSERT --
      ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.captor();
      verify(xtmOneClient).approveToolCalls(eq(CONVERSATION_ID), captor.capture());
      assertThat(captor.getValue()).hasSize(2);
      assertThat(captor.getValue().get(0)).containsEntry("tool_call_id", "toolu_1");
      assertThat(captor.getValue().get(1)).containsEntry("tool_call_id", "toolu_2");
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 409 (nothing awaiting) should relay 409")
    void given_upstreamConflict_should_return409() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.approveToolCalls(anyString(), anyList()))
          .thenReturn(
              relayed(
                  409,
                  "{\"detail\":\"No turn is currently awaiting approval for this conversation\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_APPROVE_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"conversation_id\":\""
                          + CONVERSATION_ID
                          + "\",\"decisions\":["
                          + DECISION
                          + "]}"))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/conversations/{conversationId}/pending-approvals")
  class PendingApprovals {

    private String url(String conversationId) {
      return "/api/xtmone/chat/conversations/" + conversationId + "/pending-approvals";
    }

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID conversation id should return 400 without calling XTM One")
    void given_invalidConversationId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(get(url("not-a-uuid")).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given no paused turn should return 200 with an empty proposals list")
    void given_noPausedTurn_should_returnEmptyProposals() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getPendingApprovals(CONVERSATION_ID))
          .thenReturn(
              ok(
                  Map.of(
                      "conversation_id", CONVERSATION_ID, "proposals", List.of(), "turn", "idle")));

      // -- ACT & ASSERT --
      mvc.perform(get(url(CONVERSATION_ID)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.proposals").isArray())
          .andExpect(jsonPath("$.proposals").isEmpty())
          .andExpect(jsonPath("$.turn").value("idle"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given a paused turn should return the proposals and the running turn marker")
    void given_pausedTurn_should_returnProposalsAndTurnState() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getPendingApprovals(CONVERSATION_ID))
          .thenReturn(
              ok(
                  Map.of(
                      "conversation_id",
                      CONVERSATION_ID,
                      "proposals",
                      List.of(
                          Map.of(
                              "tool_call_id",
                              "toolu_1",
                              "tool_name",
                              "delete_entity",
                              "arguments",
                              Map.of("cascade", true))),
                      "turn",
                      "running")));

      // -- ACT & ASSERT --
      mvc.perform(get(url(CONVERSATION_ID)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.proposals[0].tool_call_id").value("toolu_1"))
          .andExpect(jsonPath("$.proposals[0].tool_name").value("delete_entity"))
          .andExpect(jsonPath("$.proposals[0].arguments.cascade").value(true))
          .andExpect(jsonPath("$.turn").value("running"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 404 should relay 404 rather than an empty list")
    void given_upstreamNotFound_should_return404() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getPendingApprovals(CONVERSATION_ID))
          .thenReturn(relayed(404, "{\"detail\":\"Conversation not found\"}"));

      // -- ACT & ASSERT --
      mvc.perform(get(url(CONVERSATION_ID)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/prompts")
  class ListPrompts {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns prompts should relay them")
    void given_prompts_should_relayThem() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatPrompts())
          .thenReturn(
              ok(
                  Map.of(
                      "prompts",
                      List.of(Map.of("id", "p-1", "title", "Summarize", "content", "Summarize")))));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_PROMPTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.prompts[0].id").value("p-1"))
          .andExpect(jsonPath("$.prompts[0].content").value("Summarize"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 503 should relay 503")
    void given_upstreamUnavailable_should_return503() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatPrompts()).thenReturn(relayed(503, "{\"detail\":\"AI disabled\"}"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_PROMPTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value("AI disabled"));
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/quota")
  class GetQuota {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns a quota should relay it")
    void given_quota_should_relayIt() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatQuota())
          .thenReturn(
              new XtmOneClient.RelayedResponse(
                  200,
                  JsonNodeFactory.instance
                      .objectNode()
                      .put("used", 3)
                      .put("limit", 10)
                      .put("period", "daily")
                      .put("scope", "user")));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_QUOTA_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.used").value(3))
          .andExpect(jsonPath("$.limit").value(10))
          .andExpect(jsonPath("$.period").value("daily"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One has nothing to show should return a JSON null")
    void given_nothingToShow_should_returnNull() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatQuota())
          .thenReturn(new XtmOneClient.RelayedResponse(200, NullNode.getInstance()));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_QUOTA_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().string("null"));
    }
  }

  @Nested
  @DisplayName("POST /api/xtmone/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  class SubmitMessageFeedback {

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID message id should return 400 without calling XTM One")
    void given_invalidMessageId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, "not-a-uuid"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"positive\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given an unknown rating should return 400 without calling XTM One")
    void given_unknownRating_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"neutral\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a comment over 2000 characters should return 400 without calling XTM One")
    void given_overlongComment_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"negative\",\"comment\":\"" + "x".repeat(2001) + "\"}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given a valid rating should forward it and return the stored rating")
    void given_validRating_should_forwardAndReturnStoredRating() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.submitMessageFeedback(CONVERSATION_ID, MESSAGE_ID, "negative", "Wrong CVE"))
          .thenReturn(ok(Map.of("rating", "negative", "comment", "Wrong CVE")));

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"negative\",\"comment\":\"Wrong CVE\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.rating").value("negative"))
          .andExpect(jsonPath("$.comment").value("Wrong CVE"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 404 (message not readable) should relay 404")
    void given_upstreamNotFound_should_return404() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.submitMessageFeedback(
              eq(CONVERSATION_ID), eq(MESSAGE_ID), eq("positive"), isNull()))
          .thenReturn(relayed(404, "{\"detail\":\"Message not found\"}"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"positive\"}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Message not found"));
    }
  }

  @Nested
  @DisplayName(
      "DELETE /api/xtmone/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  class RetractMessageFeedback {

    @Test
    @WithMockUser
    @DisplayName("Given a non-UUID conversation id should return 400 without calling XTM One")
    void given_invalidConversationId_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(delete(feedbackUrl("not-a-uuid", MESSAGE_ID)).with(csrf()))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given valid ids should retract the rating and return 204")
    void given_validIds_should_returnNoContent() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID))
          .thenReturn(new XtmOneClient.RelayedResponse(204, null));

      // -- ACT & ASSERT --
      mvc.perform(delete(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)).with(csrf()))
          .andExpect(status().isNoContent());
      verify(xtmOneClient).retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID);
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 404 should relay 404")
    void given_upstreamNotFound_should_return404() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID))
          .thenReturn(relayed(404, "{\"detail\":\"Message not found\"}"));

      // -- ACT & ASSERT --
      mvc.perform(delete(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)).with(csrf()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Message not found"));
    }
  }

  @Nested
  @DisplayName("Enterprise Edition gating of the conversation and workspace routes")
  class EnterpriseEditionGating {

    private static Stream<Arguments> gatedRoutes() {
      String conversationUrl = CHAT_SESSIONS_URL + "/" + CONVERSATION_ID;
      String workspaceUrl = CHAT_WORKSPACES_URL + "/" + WORKSPACE_ID;
      return Stream.of(
          Arguments.of("POST sessions", json(post(CHAT_SESSIONS_URL), "{\"agent_slug\":\"a\"}")),
          Arguments.of("PATCH session", json(patch(conversationUrl), "{\"title\":\"Renamed\"}")),
          Arguments.of("GET workspaces", get(CHAT_WORKSPACES_URL)),
          Arguments.of(
              "POST workspaces", json(post(CHAT_WORKSPACES_URL), "{\"name\":\"Red team\"}")),
          Arguments.of("PATCH workspace", json(patch(workspaceUrl), "{\"name\":\"Blue\"}")),
          Arguments.of("DELETE workspace", delete(workspaceUrl).with(csrf())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatedRoutes")
    @WithMockUser
    @DisplayName(
        "Given an inactive Enterprise Edition license should refuse without calling XTM One")
    void given_inactiveLicense_should_refuse(String route, MockHttpServletRequestBuilder request)
        throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(request)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
      verifyNoInteractions(xtmOneClient);
    }
  }

  private static XtmOneClient.RelayedResponse relayed(int status, String json) throws Exception {
    JsonNode body = JSON.readTree(json);
    return new XtmOneClient.RelayedResponse(status, body);
  }

  /** XTM One accepting the request with this payload. */
  private static XtmOneClient.RelayedResponse ok(Object payload) {
    return new XtmOneClient.RelayedResponse(200, JSON.valueToTree(payload));
  }

  /** XTM One refusing a request the route does not relay itself, as the client reports it. */
  private static XtmOneUpstreamException refusal(int status, String detail) {
    return new XtmOneUpstreamException(
        XtmOneClient.RelayedResponse.ofDetail(status, TextNode.valueOf(detail)));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String json) {
    return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
  }

  private static String feedbackUrl(String conversationId, String messageId) {
    return "/api/xtmone/chat/conversations/"
        + conversationId
        + "/messages/"
        + messageId
        + "/feedback";
  }
}
