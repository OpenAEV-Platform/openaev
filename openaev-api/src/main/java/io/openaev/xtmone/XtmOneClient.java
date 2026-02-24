package io.openaev.xtmone;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log
public class XtmOneClient {

  private final XtmOneConfig config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(15))
          .build();

  public Map<String, Object> register(
      String platformIdentifier,
      String platformUrl,
      String platformTitle,
      String platformVersion,
      String platformId,
      String enterpriseLicensePem,
      String licenseType,
      String adminApiKey,
      List<Map<String, String>> users) {
    if (!config.isConfigured()) {
      return null;
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("platform_identifier", platformIdentifier);
      body.put("platform_url", platformUrl);
      body.put("platform_title", platformTitle);
      body.put("platform_version", platformVersion);
      body.put("platform_id", platformId != null ? platformId : "");
      body.put("enterprise_license_pem", enterpriseLicensePem != null ? enterpriseLicensePem : "");
      body.put("license_type", licenseType != null ? licenseType : "");
      body.put("admin_api_key", adminApiKey != null ? adminApiKey : "");
      body.put("users", users != null ? users : List.of());
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getUrl() + "/api/v1/platform/register"))
              .header("Authorization", "Bearer " + config.getToken())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofSeconds(15))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        return objectMapper.readValue(response.body(), Map.class);
      }
      log.warning(
          "[XTM One] Registration failed: HTTP " + response.statusCode() + " — " + response.body());
    } catch (Exception e) {
      log.warning("[XTM One] Registration error: " + e.getMessage());
    }
    return null;
  }

  public List<Map<String, Object>> listChatAgents(String userEmail) {
    if (!config.isConfigured()) {
      return List.of();
    }
    try {
      String query = "tag=openaev";
      if (userEmail != null && !userEmail.isBlank()) {
        query +=
            "&user_email="
                + java.net.URLEncoder.encode(userEmail, java.nio.charset.StandardCharsets.UTF_8);
      }
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getUrl() + "/api/v1/platform/chat/agents?" + query))
              .header("Authorization", "Bearer " + config.getToken())
              .GET()
              .timeout(Duration.ofSeconds(10))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        return objectMapper.readValue(response.body(), List.class);
      }
    } catch (Exception e) {
      log.warning("[XTM One] List chat agents error: " + e.getMessage());
    }
    return List.of();
  }

  public Map<String, Object> createChatSession(
      String userEmail, String agentSlug, String conversationId) {
    if (!config.isConfigured()) {
      return null;
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("user_email", userEmail);
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      if (conversationId != null) body.put("conversation_id", conversationId);
      body.put("content", "");
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getUrl() + "/api/v1/platform/chat/sessions"))
              .header("Authorization", "Bearer " + config.getToken())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofSeconds(10))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        return objectMapper.readValue(response.body(), Map.class);
      }
      log.warning("[XTM One] Create session failed: HTTP " + response.statusCode());
    } catch (Exception e) {
      log.warning("[XTM One] Create session error: " + e.getMessage());
    }
    return null;
  }

  public InputStream streamChatMessage(
      String userEmail, String content, String conversationId, String agentSlug) {
    if (!config.isConfigured()) {
      log.warning("[XTM One] Chat message skipped: not configured");
      return null;
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("user_email", userEmail);
      body.put("content", content);
      if (conversationId != null) body.put("conversation_id", conversationId);
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getUrl() + "/api/v1/platform/chat/messages"))
              .header("Authorization", "Bearer " + config.getToken())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofMinutes(10))
              .build();
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() == 200) {
        return response.body();
      }
      log.warning(
          "[XTM One] Chat message failed: HTTP "
              + response.statusCode()
              + " for user="
              + userEmail
              + ", agent="
              + agentSlug);
    } catch (java.net.http.HttpTimeoutException e) {
      log.warning(
          "[XTM One] Chat message timed out for user=" + userEmail + ", agent=" + agentSlug);
    } catch (Exception e) {
      log.warning(
          "[XTM One] Chat message error for user="
              + userEmail
              + ", agent="
              + agentSlug
              + ": "
              + e.getMessage());
    }
    return null;
  }
}
