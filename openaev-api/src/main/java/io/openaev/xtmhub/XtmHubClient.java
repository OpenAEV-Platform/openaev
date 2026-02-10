package io.openaev.xtmhub;

import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.annotation.PostConstruct;
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

  public XtmHubConnectivityStatus refreshRegistrationStatus(
      String platformId, String platformVersion, String token) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader("Accept", "application/json");
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);

      StringEntity httpBody = buildRefreshStatusBody(platformId, platformVersion, token);
      httpPost.setEntity(httpBody);
      return httpClient.execute(httpPost, this::parseResponseAsConnectivityStatus);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);

      return XtmHubConnectivityStatus.INACTIVE;
    }
  }

  public boolean autoRegister(
      String token,
      String platformContract,
      String platformId,
      String platformTitle,
      String platformUrl,
      String platformVersion) {
    PlatformSettings settings = platformSettingsService.findSettings();

    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
      httpPost.addHeader(XTMHUB_PLATFORM_TOKEN_HEADER, token);
      httpPost.addHeader(XTMHUB_PLATFORM_ID_HEADER, settings.getPlatformId());

      StringEntity httpBody =
          buildAutoRegisterBody(
              platformContract, platformId, platformTitle, platformUrl, platformVersion);
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
  private StringEntity buildRefreshStatusBody(
      String platformId, String platformVersion, String token) {
    String mutationBody =
        String.format(
            """
        {
          "query": "
            mutation RefreshPlatformRegistrationConnectivityStatus($input: RefreshPlatformRegistrationConnectivityStatusInput!) {
              refreshPlatformRegistrationConnectivityStatus(input: $input) {
                status
              }
            }
          ",
          "variables": {
            "input": {
              "platformId": "%s",
              "platformVersion": "%s",
              "token": "%s",
              "platformIdentifier": "%s"
            }
          }
        }
        """,
            platformId, platformVersion, token, platformIdentifier);

    JsonElement element = JsonParser.parseString(mutationBody);
    return new StringEntity(element.toString());
  }

  @NotNull
  private StringEntity buildAutoRegisterBody(
      String platformContract,
      String platformId,
      String platformTitle,
      String platformUrl,
      String platformVersion) {
    JsonObject platform = new JsonObject();
    platform.addProperty("contract", platformContract);
    platform.addProperty("id", platformId);
    platform.addProperty("title", platformTitle);
    platform.addProperty("url", platformUrl);
    platform.addProperty("version", platformVersion);

    JsonObject variables = new JsonObject();
    variables.add("platform", platform);

    JsonObject body = new JsonObject();
    body.addProperty(
        "query",
        "mutation AutoRegisterPlatform($platform: PlatformInput!) { autoRegisterPlatform(platform: $platform) { success } }");
    body.add("variables", variables);

    return new StringEntity(body.toString());
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
              .get("refreshPlatformRegistrationConnectivityStatus")
              .getAsJsonObject()
              .get("status")
              .getAsString();
      if (status.equals(XtmHubConnectivityStatus.ACTIVE.label)) {
        return XtmHubConnectivityStatus.ACTIVE;
      }

      if (status.equals(XtmHubConnectivityStatus.NOT_FOUND.label)) {
        return XtmHubConnectivityStatus.NOT_FOUND;
      }

      return XtmHubConnectivityStatus.INACTIVE;
    } catch (Exception e) {
      log.warn("Error occurred while parsing XTM Hub connectivity response: {}", e.getMessage(), e);

      return XtmHubConnectivityStatus.INACTIVE;
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
}
