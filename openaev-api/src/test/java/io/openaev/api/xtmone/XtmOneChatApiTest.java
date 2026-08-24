package io.openaev.api.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * Upstream rejects a partial set outright, because resuming with an undecided call leaves a
     * {@code tool_use} block without its {@code tool_result} and the model providers refuse that.
     * An empty set is the degenerate case of the same rule, so it is stopped here rather than spent
     * on a round trip.
     */
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

    /**
     * {@code tool_call_id} is the key every decision is sent back on, and never the tool name: one
     * turn can propose the same tool twice with different arguments. A decision that names no call
     * cannot be applied to one.
     */
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

    /**
     * On a human-consent control an unrecognised verdict must be refused here, rather than leaving
     * upstream's leniency as the only thing between an undecided call and an executed one.
     */
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

    /** Case matters: the verdicts are wire values, not display text. */
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

    /**
     * The rejection reason is the agent's only correction channel — there is deliberately no "edit
     * the arguments" verdict — so it has to survive the proxy hop rather than being dropped with
     * the rest of the unrecognised fields.
     */
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

    /**
     * A reason means nothing beside an approval, and upstream reads the field only on the reject
     * path. The decision itself still stands — dropping the field is not grounds to refuse a
     * consent the reviewer did give.
     */
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

    /**
     * 409 means nothing is awaiting a decision — finished, cancelled, or already answered. It has
     * to reach the client distinctly so the panel refreshes instead of retrying: a second decision
     * on a settled call has nothing to apply to, and swallowing it would tell the user they
     * approved something that never ran.
     */
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

    /** An empty list is the ordinary answer — most conversations are not paused. */
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

    /**
     * The turn marker is the completion signal for a recovered prompt: the resumed turn writes to
     * the connection that carried the original prompt, which the reload closed, so the panel polls
     * this until it reads idle instead of guessing how long to wait. It has to survive the proxy.
     */
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

    /**
     * Upstream answers 404 rather than 403 for someone else's conversation, so it never confirms
     * that one exists. Propagated rather than degraded to an empty list: a masked failure would
     * read as "nothing is pending" and leave a genuinely paused turn invisible.
     */
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
}
