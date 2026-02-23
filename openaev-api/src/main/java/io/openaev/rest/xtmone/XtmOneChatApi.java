package io.openaev.rest.xtmone;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class XtmOneChatApi extends RestBehavior {

  private final XtmOneClient client;
  private final XtmOneConfig config;
  private final UserRepository userRepository;

  private String currentUserEmail() {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    return user.getEmail();
  }

  @GetMapping("/api/xtmone/chat/agents")
  public List<Map<String, Object>> listAgents() {
    if (!config.isConfigured()) {
      return List.of();
    }
    return client.listChatAgents();
  }

  @PostMapping("/api/xtmone/chat/sessions")
  public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    String userEmail = currentUserEmail();
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    Map<String, Object> result = client.createChatSession(userEmail, agentSlug, conversationId);
    if (result == null) {
      return ResponseEntity.internalServerError().build();
    }
    return ResponseEntity.ok(result);
  }

  @PostMapping(path = "/api/xtmone/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<StreamingResponseBody> sendMessage(@RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    String userEmail = currentUserEmail();
    String content = body.get("content") != null ? body.get("content").toString() : "";
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;

    InputStream sseStream = client.streamChatMessage(userEmail, content, conversationId, agentSlug);
    if (sseStream == null) {
      return ResponseEntity.internalServerError().build();
    }

    StreamingResponseBody responseBody =
        outputStream -> {
          try (sseStream) {
            sseStream.transferTo(outputStream);
          }
        };

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header("Cache-Control", "no-cache")
        .header("X-Accel-Buffering", "no")
        .body(responseBody);
  }
}
