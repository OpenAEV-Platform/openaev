package io.openaev.api.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
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
import org.mockito.ArgumentCaptor;
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
  private static final String CHAT_APPROVE_URL = "/api/xtmone/chat/messages/approve";
  private static final String CHAT_PROMPTS_URL = "/api/xtmone/chat/prompts";
  private static final String CHAT_QUOTA_URL = "/api/xtmone/chat/quota";
  private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";
  private static final String MESSAGE_ID = "22222222-2222-2222-2222-222222222222";

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

  @Nested
  @DisplayName("POST /api/xtmone/chat/messages/approve")
  class ApproveToolCalls {

    private static final String DECISION =
        "{\"tool_call_id\":\"toolu_1\",\"decision\":\"approve\"}";

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

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
          .andExpect(status().isBadRequest());
    }

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
          .thenReturn(Map.of("status", "accepted", "decided", 1));

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
          .thenReturn(Map.of("status", "accepted"));

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
          .thenReturn(Map.of("status", "accepted"));

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
          .thenReturn(Map.of("status", "accepted", "decided", 2));

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
    @DisplayName("Given upstream answers 409 (nothing awaiting) should propagate 409")
    void given_upstreamConflict_should_return409() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.approveToolCalls(anyString(), anyList()))
          .thenThrow(
              new ResponseStatusException(
                  HttpStatus.CONFLICT,
                  "No turn is currently awaiting approval for this conversation"));

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
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(get(url(CONVERSATION_ID)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
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
              Map.of("conversation_id", CONVERSATION_ID, "proposals", List.of(), "turn", "idle"));

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
                  "running"));

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
    @DisplayName("Given upstream answers 404 should propagate 404 rather than an empty list")
    void given_upstreamNotFound_should_return404() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getPendingApprovals(CONVERSATION_ID))
          .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

      // -- ACT & ASSERT --
      mvc.perform(get(url(CONVERSATION_ID)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/prompts")
  class ListPrompts {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return an empty prompt list")
    void given_notConfigured_should_returnEmptyPrompts() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_PROMPTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.prompts").isArray())
          .andExpect(jsonPath("$.prompts").isEmpty());
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns prompts should relay them")
    void given_prompts_should_relayThem() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatPrompts())
          .thenReturn(
              Map.of(
                  "prompts",
                  List.of(Map.of("id", "p-1", "title", "Summarize", "content", "Summarize"))));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_PROMPTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.prompts[0].id").value("p-1"))
          .andExpect(jsonPath("$.prompts[0].content").value("Summarize"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 503 should propagate 503")
    void given_upstreamUnavailable_should_return503() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatPrompts())
          .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI disabled"));

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_PROMPTS_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isServiceUnavailable());
    }
  }

  @Nested
  @DisplayName("GET /api/xtmone/chat/quota")
  class GetQuota {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return a JSON null")
    void given_notConfigured_should_returnNull() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(get(CHAT_QUOTA_URL).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().string("null"));
      verifyNoInteractions(xtmOneClient);
    }

    @Test
    @WithMockUser
    @DisplayName("Given XTM One returns a quota should relay it")
    void given_quota_should_relayIt() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.getChatQuota())
          .thenReturn(
              JsonNodeFactory.instance
                  .objectNode()
                  .put("used", 3)
                  .put("limit", 10)
                  .put("period", "daily")
                  .put("scope", "user"));

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
      when(xtmOneClient.getChatQuota()).thenReturn(NullNode.getInstance());

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
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"positive\"}"))
          .andExpect(status().isBadRequest());
    }

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
          .thenReturn(Map.of("rating", "negative", "comment", "Wrong CVE"));

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
    @DisplayName("Given upstream answers 404 (message not readable) should propagate 404")
    void given_upstreamNotFound_should_return404() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      when(xtmOneClient.submitMessageFeedback(
              eq(CONVERSATION_ID), eq(MESSAGE_ID), eq("positive"), isNull()))
          .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

      // -- ACT & ASSERT --
      mvc.perform(
              post(feedbackUrl(CONVERSATION_ID, MESSAGE_ID))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":\"positive\"}"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName(
      "DELETE /api/xtmone/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  class RetractMessageFeedback {

    @Test
    @WithMockUser
    @DisplayName("Given XTM One not configured should return 400")
    void given_notConfigured_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      mvc.perform(delete(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)).with(csrf()))
          .andExpect(status().isBadRequest());
    }

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

      // -- ACT & ASSERT --
      mvc.perform(delete(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)).with(csrf()))
          .andExpect(status().isNoContent());
      verify(xtmOneClient).retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID);
    }

    @Test
    @WithMockUser
    @DisplayName("Given upstream answers 404 should propagate 404")
    void given_upstreamNotFound_should_return404() throws Exception {
      // -- ARRANGE --
      when(xtmOneConfig.isConfigured()).thenReturn(true);
      doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"))
          .when(xtmOneClient)
          .retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID);

      // -- ACT & ASSERT --
      mvc.perform(delete(feedbackUrl(CONVERSATION_ID, MESSAGE_ID)).with(csrf()))
          .andExpect(status().isNotFound());
    }
  }

  private static String feedbackUrl(String conversationId, String messageId) {
    return "/api/xtmone/chat/conversations/"
        + conversationId
        + "/messages/"
        + messageId
        + "/feedback";
  }
}
