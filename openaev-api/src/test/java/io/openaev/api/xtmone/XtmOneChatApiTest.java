package io.openaev.api.xtmone;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@TestInstance(PER_CLASS)
@DisplayName("XTM One Chat API tests")
class XtmOneChatApiTest extends IntegrationTest {

  private static final String CHAT_AGENTS_URL = "/api/xtmone/chat/agents";
  private static final String CHAT_SESSIONS_URL = "/api/xtmone/chat/sessions";
  private static final String CHAT_STEER_URL = "/api/xtmone/chat/messages/steer";
  private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";

  @Autowired private MockMvc mvc;
  @MockitoBean private XtmOneClient xtmOneClient;
  @MockitoBean private XtmOneConfig xtmOneConfig;

  // The new history/steering endpoints are EE-gated (@AccessControl(isEnterpriseEdition = true)).
  // The mock's isEnterpriseLicenseInactive() returns false by default, i.e. an active EE license.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Nested
  @DisplayName("GET /api/xtmone/chat/agents")
  class ListAgents {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return 200 with empty list")
    void given_notConfigured_should_returnEmptyList() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_AGENTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$").isEmpty());
    }

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
    @DisplayName("Given XTM One returns 503 should propagate 503 to client")
    void given_xtmOneReturns503_should_return503() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.issueAuthenticationJwt(anyString(), anyString(), anyString()))
          .thenReturn("fake-jwt");
      when(xtmOneClient.listChatAgents(anyString()))
          .thenThrow(
              new ResponseStatusException(
                  HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service unavailable"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_AGENTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns 401 should propagate UNAUTHORIZED to client")
    void given_xtmOneReturns401_should_return401() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.issueAuthenticationJwt(anyString(), anyString(), anyString()))
          .thenReturn("fake-jwt");
      when(xtmOneClient.listChatAgents(anyString()))
          .thenThrow(
              new ResponseStatusException(
                  HttpStatus.UNAUTHORIZED, "[XTM One] Unauthorized access to chat agents"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_AGENTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnauthorized());
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
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(get(DOWNLOAD_URL)).andExpect(status().isBadRequest());
    }

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
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/sessions")
  class ListSessions {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return 200 with empty history")
    void given_notConfigured_should_returnEmptyHistory() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_SESSIONS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversations").isArray())
          .andExpect(jsonPath("$.conversations").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream failure (null) should degrade to 200 with empty history")
    void given_upstreamFailure_should_degradeToEmptyHistory() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatSessions()).thenReturn(null);

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_SESSIONS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversations").isArray())
          .andExpect(jsonPath("$.conversations").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream conversations should return the payload as-is")
    void given_conversations_should_returnPayload() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.listChatSessions())
          .thenReturn(
              Map.of(
                  "conversations",
                  List.of(Map.of("conversation_id", CONVERSATION_ID, "title", "My conversation"))));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_SESSIONS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversations.length()").value(1))
          .andExpect(jsonPath("$.conversations[0].conversation_id").value(CONVERSATION_ID))
          .andExpect(jsonPath("$.conversations[0].title").value("My conversation"));
    }
  }

  @Nested
  @DisplayName("DELETE /api/xtmone/chat/sessions/{conversationId}")
  class DeleteSession {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID).with(csrf()))
          .andExpect(status().isBadRequest());
    }

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
      when(xtmOneClient.deleteChatSession(CONVERSATION_ID)).thenReturn(true);

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID).with(csrf()))
          .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream rejects the deletion should return 500")
    void given_upstreamRejects_should_returnInternalServerError() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.deleteChatSession(CONVERSATION_ID)).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(delete(CHAT_SESSIONS_URL + "/" + CONVERSATION_ID).with(csrf()))
          .andExpect(status().isInternalServerError());
    }
  }

  @Nested
  @DisplayName("POST /api/xtmone/chat/messages/steer")
  class SteerMessage {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_STEER_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"content\":\"hello\",\"conversation_id\":\"" + CONVERSATION_ID + "\"}"))
          .andExpect(status().isBadRequest());
    }

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
          .thenReturn(Map.of("status", "queued"));

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
    @DisplayName("Given upstream answers 409 (no run active) should propagate 409 to client")
    void given_upstreamConflict_should_return409() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.steerChatMessage("hello", CONVERSATION_ID))
          .thenThrow(
              new ResponseStatusException(
                  HttpStatus.CONFLICT, "No response is currently being generated"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(CHAT_STEER_URL)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"content\":\"hello\",\"conversation_id\":\"" + CONVERSATION_ID + "\"}"))
          .andExpect(status().isConflict());
    }
  }
}
