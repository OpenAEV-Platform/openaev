package io.openaev.rest.xtmone;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequiredArgsConstructor
public class XtmOneChatApi extends RestBehavior {

  private static final String XTM_ONE_URI = "/api/xtmone";
  private final XtmOneClient client;
  private final XtmOneConfig config;
  private final UserRepository userRepository;

  private User resolveCurrentUser() {
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  private String issueJwt(User user) {
    return client.issueAuthenticationJwt(
        user.getId(), user.getName() != null ? user.getName() : user.getEmail(), user.getEmail());
  }

  @GetMapping(XTM_ONE_URI + "/chat/agents")
  public ResponseEntity<List<Map<String, Object>>> listAgents() {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(List.of());
    }
    User user = resolveCurrentUser();
    return ResponseEntity.ok(client.listChatAgents(issueJwt(user)));
  }

  @PostMapping(XTM_ONE_URI + "/chat/sessions")
  public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    User user = resolveCurrentUser();
    String jwt = issueJwt(user);
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    Map<String, Object> result = client.createChatSession(jwt, agentSlug, conversationId);
    if (result == null) {
      return ResponseEntity.internalServerError().build();
    }
    return ResponseEntity.ok(result);
  }

  @PostMapping(path = XTM_ONE_URI + "/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<StreamingResponseBody> sendMessage(@RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    User user = resolveCurrentUser();
    String jwt = issueJwt(user);
    String content = body.get("content") != null ? body.get("content").toString() : "";
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;

    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
                jwt,
                content,
                conversationId,
                agentSlug,
                sseStream -> {
                  byte[] buf = new byte[4096];
                  int n;
                  while ((n = sseStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, n);
                    outputStream.flush();
                  }
                });
          } catch (Exception e) {
            log.warn("[XTM One Chat] Stream error, agent={}.", agentSlug, e);
            outputStream.write(
                ("data: "
                        + "{\"type\":\"error\",\"content\":\"Unable to connect to the AI assistant. Please try again.\"}"
                        + "\n\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();
          }
        };

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header("Cache-Control", "no-cache")
        .header("X-Accel-Buffering", "no")
        .body(responseBody);
  }
}
