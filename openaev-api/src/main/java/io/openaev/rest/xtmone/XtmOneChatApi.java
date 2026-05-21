package io.openaev.rest.xtmone;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
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

  @GetMapping(XTM_ONE_URI + "/chat/agents")
  public ResponseEntity<List<ChatbotAgentOutput>> listAgents() {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(List.of());
    }
    return ResponseEntity.ok(client.listChatAgents("global.assistant"));
  }

  @PostMapping(XTM_ONE_URI + "/chat/sessions")
  public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    Map<String, Object> result = client.createChatSession(agentSlug, conversationId);
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
    String content = body.get("content") != null ? body.get("content").toString() : "";
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;

    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
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
          } catch (ResponseStatusException e) {
            String detail =
                e.getReason() != null && !e.getReason().isBlank()
                    ? e.getReason()
                    : "Unable to connect to the AI assistant. Please try again.";
            String errorContent =
                e.getStatusCode().value() == 429
                    ? "⚠️ **Quota exceeded** — " + detail
                    : "⚠️ **Error** — " + detail;
            outputStream.write(
                ("data: {\"type\":\"error\",\"content\":\"" + errorContent + "\"}\n\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();
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

  @PostMapping(path = XTM_ONE_URI + "/chat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadFiles(
      @RequestParam("conversation_id") String conversationId, MultipartHttpServletRequest request) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (conversationId.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    List<MultipartFile> requestedFiles =
        request.getMultiFileMap().values().stream()
            .flatMap(List::stream)
            .filter(file -> file != null && !file.isEmpty())
            .toList();
    if (requestedFiles.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    List<String> fileIds = new ArrayList<>();
    for (MultipartFile file : requestedFiles) {
      String fileId = client.uploadChatFile(conversationId, file);
      if (fileId != null && !fileId.isBlank()) {
        fileIds.add(fileId);
      }
    }

    if (fileIds.isEmpty()) {
      return ResponseEntity.internalServerError().build();
    }
    return ResponseEntity.ok(Map.of("file_ids", fileIds));
  }
}
