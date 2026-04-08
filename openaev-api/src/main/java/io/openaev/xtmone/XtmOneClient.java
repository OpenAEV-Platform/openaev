package io.openaev.xtmone;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class XtmOneClient {

  private final XtmOneConfig config;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper mapper;

  // ── Request / Response DTOs ──────────────────────────────────────────

  @Data
  public static class IntentInput {
    private String name;
    private String description;

    public IntentInput(String name, String description) {
      this.name = name;
      this.description = description;
    }
  }

  @Data
  public static class RegistrationInput {
    @JsonProperty("platform_identifier")
    private String platformIdentifier;

    @JsonProperty("platform_url")
    private String platformUrl;

    @JsonProperty("platform_title")
    private String platformTitle;

    @JsonProperty("platform_version")
    private String platformVersion;

    @JsonProperty("platform_id")
    private String platformId;

    @JsonProperty("enterprise_license_pem")
    private String enterpriseLicensePem;

    @JsonProperty("license_type")
    private String licenseType;

    @JsonProperty("business_vertical")
    private String businessVertical;

    private List<IntentInput> intents;
  }

  @Data
  public static class IntentCatalogAgent {
    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("agent_name")
    private String agentName;

    @JsonProperty("agent_slug")
    private String agentSlug;

    @JsonProperty("agent_description")
    private String agentDescription;

    private String vertical;
    private int priority;

    @JsonProperty("is_default")
    private boolean isDefault;

    @JsonProperty("is_locked")
    private boolean isLocked;

    private boolean enabled;
  }

  @Data
  public static class IntentCatalogEntry {
    private String intent;
    private String description;
    private List<IntentCatalogAgent> agents;
  }

  @Data
  public static class RegistrationResponse {
    private String status;

    @JsonProperty("platform_identifier")
    private String platformIdentifier;

    @JsonProperty("ee_enabled")
    private boolean eeEnabled;

    @JsonProperty("user_integrations")
    private int userIntegrations;

    @JsonProperty("chat_web_token")
    private String chatWebToken;

    @JsonProperty("intent_catalog")
    private List<IntentCatalogEntry> intentCatalog;
  }

  // ── Registration ─────────────────────────────────────────────────────

  public RegistrationResponse register(RegistrationInput input) {
    if (!config.isConfigured()) {
      return null;
    }

    String url = config.getUrl().replaceAll("/+$", "") + "/api/v1/platform/register";

    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(url);
      httpPost.addHeader("Authorization", "Bearer " + config.getToken());
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.setConfig(
          RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(15)).build());

      String body = mapper.writeValueAsString(input);
      httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

      return httpClient.execute(
          httpPost,
          (ClassicHttpResponse response) -> {
            int status = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            if (status >= 200 && status < 300) {
              return mapper.readValue(responseBody, RegistrationResponse.class);
            } else {
              log.error(
                  "[XTM One] Registration failed with status {}: {}",
                  status,
                  responseBody);
              return null;
            }
          });
    } catch (Exception e) {
      log.error("[XTM One] Registration failed: {}", e.getMessage(), e);
      return null;
    }
  }
}
