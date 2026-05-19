package io.openaev.rest.xtmone;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
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

  @Autowired private MockMvc mvc;
  @MockitoBean private XtmOneClient xtmOneClient;
  @MockitoBean private XtmOneConfig xtmOneConfig;

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
      List<Map<String, Object>> agents =
          List.of(
              Map.of("id", "agent-1", "name", "Test Agent", "slug", "test-agent"),
              Map.of("id", "agent-2", "name", "Another Agent", "slug", "another-agent"));
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
}
