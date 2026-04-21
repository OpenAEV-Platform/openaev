package io.openaev.xtmone;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class XtmOneClient {

  private final XtmOneConfig config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(15))
          .build();

  private volatile KeyPair ed25519KeyPair;

  private synchronized KeyPair getOrCreateKeyPair() {
    if (ed25519KeyPair == null) {
      try {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        ed25519KeyPair = gen.generateKeyPair();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to generate Ed25519 key pair", e);
      }
    }
    return ed25519KeyPair;
  }

  public String issueAuthenticationJwt(String userId, String userName, String userEmail) {
    var kp = getOrCreateKeyPair();
    var now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("name", userName)
        .claim("email", userEmail)
        .issuer("openaev")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofHours(1))))
        .signWith(kp.getPrivate())
        .compact();
  }

  private HttpRequest.Builder chatRequestBuilder(String path, String jwt) {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(config.getUrl() + path))
            .header("Authorization", "Bearer " + jwt)
            .header("Content-Type", "application/json")
            .header("X-Platform-Product", "openaev")
            .header(
                "X-Platform-URL", config.getPlatformUrl() != null ? config.getPlatformUrl() : "");
    var version = config.getPlatformVersion();
    if (version != null && !version.isBlank()) {
      builder.header("X-Platform-Version", version);
    }
    return builder;
  }

  public Map<String, Object> register(
      String platformIdentifier,
      String platformUrl,
      String platformTitle,
      String platformVersion,
      String platformId,
      String enterpriseLicensePem,
      String licenseType,
      String businessVertical,
      List<Map<String, String>> intents) {
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
      if (businessVertical != null) body.put("business_vertical", businessVertical);
      body.put("intents", intents != null ? intents : List.of());
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
      log.warn(
          "[XTM One] Registration failed: HTTP {} — {}", response.statusCode(), response.body());
    } catch (Exception e) {
      log.warn("[XTM One] Registration error.", e);
    }
    return null;
  }

  public List<Map<String, Object>> listChatAgents(String jwt) {
    if (!config.isConfigured()) {
      return List.of();
    }
    try {
      HttpRequest request =
          chatRequestBuilder("/api/v1/platform/chat/agents?tag=openaev", jwt)
              .GET()
              .timeout(Duration.ofSeconds(10))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        return objectMapper.readValue(response.body(), List.class);
      }
      log.warn("[XTM One] List chat agents failed: HTTP {}", response.statusCode());
    } catch (Exception e) {
      log.warn("[XTM One] List chat agents error.", e);
    }
    return List.of();
  }

  public Map<String, Object> createChatSession(
      String jwt, String agentSlug, String conversationId) {
    if (!config.isConfigured()) {
      return null;
    }
    try {
      Map<String, Object> body = new HashMap<>();
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      if (conversationId != null) body.put("conversation_id", conversationId);
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          chatRequestBuilder("/api/v1/platform/chat/sessions", jwt)
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofSeconds(10))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        return objectMapper.readValue(response.body(), Map.class);
      }
      log.warn("[XTM One] Create session failed: HTTP {}", response.statusCode());
    } catch (Exception e) {
      log.warn("[XTM One] Create session error: ", e);
    }
    return null;
  }

  public InputStream streamChatMessage(
      String jwt, String content, String conversationId, String agentSlug) {
    if (!config.isConfigured()) {
      log.warn("[XTM One] Chat message skipped: not configured");
      return null;
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("content", content);
      if (conversationId != null) body.put("conversation_id", conversationId);
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          chatRequestBuilder("/api/v1/platform/chat/messages", jwt)
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofMinutes(15))
              .build();
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() == 200) {
        return response.body();
      }
      log.warn(
          "[XTM One] Chat message failed: HTTP {}, agent={}", response.statusCode(), agentSlug);
    } catch (java.net.http.HttpTimeoutException e) {
      log.warn("[XTM One] Chat message timed out, agent={}", agentSlug, e);
    } catch (Exception e) {
      log.warn("[XTM One] Chat message error, agent={}.", agentSlug, e);
    }
    return null;
  }
}
