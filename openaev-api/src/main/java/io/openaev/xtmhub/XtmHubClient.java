package io.openaev.xtmhub;

import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class XtmHubClient {
  private final XtmHubConfig config;
  private final HttpClientFactory httpClientFactory;
  private static final String platformIdentifier = "openaev";
  private final PlatformSettingsService platformSettingsService;
  private static final String GRAPHQL_PATH = "/graphql-api";
  private String graphqlEndpoint;
  private static final String XTMHUB_PLATFORM_TOKEN_HEADER = "XTM-Hub-Platform-Token";
  private static final String XTMHUB_PLATFORM_ID_HEADER = "XTM-Hub-Platform-Id";

  @PostConstruct
  void init() {
    this.graphqlEndpoint = config.getApiUrl() + GRAPHQL_PATH;
  }

  public Boolean contactUs(String message, String token, String platformId) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader("Content-Type", "application/json; charset=utf-8");
      httpPost.addHeader("Accept", "application/json");
      httpPost.addHeader(XTMHUB_PLATFORM_TOKEN_HEADER, token);
      httpPost.addHeader(XTMHUB_PLATFORM_ID_HEADER, platformId);
      StringEntity httpBody = buildMutationContactUsBody(message);
      httpPost.setEntity(httpBody);
      return httpClient.execute(httpPost, this::isContactUsResponseSuccessful);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);
      return false;
    }
  }

  public XtmHubConnectivityStatus refreshRegistrationStatusSingleTenant(
      String platformId, String platformVersion, String token, String url, String tenantId) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader("Accept", "application/json");
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);

      StringEntity httpBody =
          buildRefreshStatusSingleTenantBody(platformId, platformVersion, token, url, tenantId);
      httpPost.setEntity(httpBody);
      return httpClient.execute(httpPost, this::parseResponseAsConnectivityStatus);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);

      return XtmHubConnectivityStatus.INACTIVE;
    }
  }

  public Map<String, XtmHubConnectivityStatus> refreshRegistrationStatusAllTenants(
      String platformId, String platformVersion, Map<String, TenantRegistrationDetails> tenants) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader("Accept", "application/json");
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);

      StringEntity httpBody =
          buildRefreshStatusAllTenantsBody(platformId, platformVersion, tenants);
      httpPost.setEntity(httpBody);
      return httpClient.execute(httpPost, this::parseAllTenantsResponseAsConnectivityStatus);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);
      return Map.of();
    }
  }

  public boolean autoRegister(
      String token,
      String platformContract,
      String platformId,
      String platformTitle,
      String platformUrl,
      String platformVersion,
      String tenantId,
      Long usersCount) {
    PlatformSettings settings = platformSettingsService.findSettings();

    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
      httpPost.addHeader(XTMHUB_PLATFORM_TOKEN_HEADER, token);
      httpPost.addHeader(XTMHUB_PLATFORM_ID_HEADER, settings.getPlatformId());

      StringEntity httpBody =
          buildAutoRegisterBody(
              platformContract,
              platformId,
              platformTitle,
              platformUrl,
              platformVersion,
              tenantId,
              usersCount);
      httpPost.setEntity(httpBody);
      return httpClient.execute(httpPost, this::parseResponseAsSuccess);
    } catch (Exception e) {
      log.error("Failed to auto-register on {}: {}", config.getApiUrl(), e.getMessage(), e);
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_GATEWAY,
          "Failed to auto-register XtmHub" + e.getMessage());
    }
  }

  @NotNull
  private StringEntity buildMutationContactUsBody(String message) {
    String mutationBody =
        String.format(
            """
    {
      "query": "
        mutation ContactUsXTMHub($message: String!) {
          contactUs(message: $message) {
            success
          }
        }
      ",
      "variables": {
        "message": "%s",
        "platform_identifier": "%s"
      }
    }
    """,
            message, "openaev");

    JsonElement element = JsonParser.parseString(mutationBody);
    return new StringEntity(element.toString());
  }

  @NotNull
  private StringEntity buildRefreshStatusSingleTenantBody(
      String platformId, String platformVersion, String token, String url, String tenantId) {
    String mutationBody =
        String.format(
            """
                {
                  "query": "
                    mutation refreshPlatformRegistrationConnectivityStatusSingleTenant($input: RefreshPlatformRegistrationConnectivityStatusSingleTenantInput!) {
                      refreshPlatformRegistrationConnectivityStatusSingleTenant(input: $input) {
                        status
                      }
                    }
                  ",
                  "variables": {
                    "input": {
                      "platformId": "%s",
                      "platformVersion": "%s",
                      "token": "%s",
                      "platformIdentifier": "%s",
                      "url": "%s",
                      "tenantId": "%s"
                    }
                  }
                }
                """,
            platformId, platformVersion, token, platformIdentifier, url, tenantId);

    JsonElement element = JsonParser.parseString(mutationBody);
    return new StringEntity(element.toString());
  }

  @NotNull
  private StringEntity buildRefreshStatusAllTenantsBody(
      String platformId, String platformVersion, Map<String, TenantRegistrationDetails> tenants) {

    JsonArray tenantsArray = new JsonArray();
    tenants.forEach(
        (tenantId, details) -> {
          JsonObject tenantToken = new JsonObject();
          tenantToken.addProperty("tenantId", tenantId);
          tenantToken.addProperty("token", details.token());
          tenantToken.addProperty("url", details.url());
          tenantsArray.add(tenantToken);
        });

    JsonObject input = new JsonObject();
    input.addProperty("platformId", platformId);
    input.addProperty("platformVersion", platformVersion);
    input.addProperty("platformIdentifier", platformIdentifier);
    input.add("tenants", tenantsArray);

    JsonObject variables = new JsonObject();
    variables.add("input", input);

    JsonObject body = new JsonObject();
    body.addProperty(
        "query",
        "mutation refreshPlatformRegistrationConnectivityStatusAllTenants($input: RefreshPlatformRegistrationConnectivityStatusAllTenantsInput!) { refreshPlatformRegistrationConnectivityStatusAllTenants(input: $input) { statuses { tenantId status } } }");
    body.add("variables", variables);

    return new StringEntity(body.toString());
  }

  @NotNull
  private StringEntity buildAutoRegisterBody(
      String platformContract,
      String platformId,
      String platformTitle,
      String platformUrl,
      String platformVersion,
      String tenantId,
      Long usersCount) {

    JsonObject platform = new JsonObject();
    platform.addProperty("contract", platformContract);
    platform.addProperty("id", platformId);
    platform.addProperty("title", platformTitle);
    platform.addProperty("url", platformUrl);
    platform.addProperty("version", platformVersion);
    platform.addProperty("tenantId", tenantId);

    JsonObject input = new JsonObject();
    input.add("platform", platform);
    input.addProperty("existing_users_count", usersCount);

    JsonObject variables = new JsonObject();
    variables.add("input", input);

    JsonObject body = new JsonObject();
    body.addProperty(
        "query",
        "mutation AutoRegisterPlatform($input: AutoRegisterPlatformInput!) { autoRegisterPlatform(input: $input) { success } }");
    body.add("variables", variables);

    return new StringEntity(body.toString());
  }

  private XtmHubConnectivityStatus toConnectivityStatus(String status) {
    if (status.equals(XtmHubConnectivityStatus.ACTIVE.label)) {
      return XtmHubConnectivityStatus.ACTIVE;
    }
    if (status.equals(XtmHubConnectivityStatus.NOT_FOUND.label)) {
      return XtmHubConnectivityStatus.NOT_FOUND;
    }
    return XtmHubConnectivityStatus.INACTIVE;
  }

  private XtmHubConnectivityStatus parseResponseAsConnectivityStatus(ClassicHttpResponse response) {
    if (response.getCode() != HttpStatus.SC_OK) {
      return XtmHubConnectivityStatus.INACTIVE;
    }
    try {
      HttpEntity entity = response.getEntity();
      String responseString = EntityUtils.toString(entity, "UTF-8");
      JsonElement jsonResponse = JsonParser.parseString(responseString);
      String status =
          jsonResponse
              .getAsJsonObject()
              .get("data")
              .getAsJsonObject()
              .get("refreshPlatformRegistrationConnectivityStatusSingleTenant")
              .getAsJsonObject()
              .get("status")
              .getAsString();
      return toConnectivityStatus(status);
    } catch (Exception e) {
      log.warn("Error occurred while parsing XTM Hub connectivity response: {}", e.getMessage(), e);
      return XtmHubConnectivityStatus.INACTIVE;
    }
  }

  private Map<String, XtmHubConnectivityStatus> parseAllTenantsResponseAsConnectivityStatus(
      ClassicHttpResponse response) {
    if (response.getCode() != HttpStatus.SC_OK) {
      return Map.of();
    }
    try {
      HttpEntity entity = response.getEntity();
      String responseString = EntityUtils.toString(entity, "UTF-8");
      JsonElement jsonResponse = JsonParser.parseString(responseString);
      JsonArray statuses =
          jsonResponse
              .getAsJsonObject()
              .get("data")
              .getAsJsonObject()
              .get("refreshPlatformRegistrationConnectivityStatusAllTenants")
              .getAsJsonObject()
              .get("statuses")
              .getAsJsonArray();

      Map<String, XtmHubConnectivityStatus> result = new HashMap<>();
      statuses.forEach(
          element -> {
            JsonObject tenantStatus = element.getAsJsonObject();
            String tenantId = tenantStatus.get("tenantId").getAsString();
            String status = tenantStatus.get("status").getAsString();
            result.put(tenantId, toConnectivityStatus(status));
          });
      return result;
    } catch (Exception e) {
      log.warn(
          "Error occurred while parsing XTM Hub all-tenants connectivity response: {}",
          e.getMessage(),
          e);
      return Map.of();
    }
  }

  private boolean parseResponseAsSuccess(ClassicHttpResponse response) {
    if (response.getCode() != HttpStatus.SC_OK) {
      return false;
    }
    try {
      HttpEntity entity = response.getEntity();
      String responseString = EntityUtils.toString(entity);
      JsonElement jsonResponse = JsonParser.parseString(responseString);

      return jsonResponse
          .getAsJsonObject()
          .get("data")
          .getAsJsonObject()
          .get("autoRegisterPlatform")
          .getAsJsonObject()
          .get("success")
          .getAsBoolean();
    } catch (Exception e) {
      log.warn("Error occurred while parsing XTM Hub success response: {}", e.getMessage(), e);
      return false;
    }
  }

  private Boolean isContactUsResponseSuccessful(ClassicHttpResponse response) {
    return response.getCode() == HttpStatus.SC_OK;
  }
}
