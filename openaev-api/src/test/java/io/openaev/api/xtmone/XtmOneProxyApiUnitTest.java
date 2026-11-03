package io.openaev.api.xtmone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.xtmone.dto.AgentCallInput;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@DisplayName("XTM One proxy stream errors")
class XtmOneProxyApiUnitTest {

  private static final String AGENT = "triage-agent";

  private final XtmOneConfig config = mock(XtmOneConfig.class);
  private final XtmOneClient client = mock(XtmOneClient.class);
  private final XtmOneProxyApi api =
      new XtmOneProxyApi(config, client, mock(AiMetricCollector.class));

  /** Streams the agent call while XTM One refuses it, and returns the SSE events written. */
  private List<String> eventsWhenRefused(HttpStatus status, String reason) throws Exception {
    when(config.isConfigured()).thenReturn(true);
    doThrow(new ResponseStatusException(status, reason))
        .when(client)
        .streamChatMessage(eq("hello"), isNull(), eq(AGENT), any());
    ResponseEntity<StreamingResponseBody> response =
        api.postAgentStream(null, new AgentCallInput(AGENT, "hello", null));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    response.getBody().writeTo(out);
    String stream = out.toString(StandardCharsets.UTF_8);
    return Arrays.stream(stream.split("\n\n")).filter(e -> !e.isEmpty()).toList();
  }

  private static JsonNode data(String event) throws Exception {
    assertEquals("data: ", event.substring(0, 6));
    return new ObjectMapper().readTree(event.substring(6));
  }

  @Test
  @DisplayName("Given a detail with quotes and line breaks should write it as one valid JSON event")
  void given_detailWithQuotesAndLineBreaks_should_writeOneJsonEvent() throws Exception {
    // A detail relayed as is used to be pasted into the JSON: a quote broke it, and a blank line
    // ended the event, so what followed read as an event of its own.
    String detail = "Agent \"triage\" failed\n\ndata: {\"type\":\"done\"}";

    List<String> events = eventsWhenRefused(HttpStatus.BAD_GATEWAY, detail);

    assertEquals(1, events.size());
    JsonNode event = data(events.get(0));
    assertEquals("error", event.get("type").asText());
    assertEquals("⚠️ **Error** — " + detail, event.get("content").asText());
  }

  @Test
  @DisplayName("Given a quota refusal should say the quota is exceeded")
  void given_quotaRefusal_should_sayQuotaExceeded() throws Exception {
    List<String> events = eventsWhenRefused(HttpStatus.TOO_MANY_REQUESTS, "Daily limit \"30\"");

    assertEquals(1, events.size());
    assertEquals(
        "⚠️ **Quota exceeded** — Daily limit \"30\"", data(events.get(0)).get("content").asText());
  }
}
