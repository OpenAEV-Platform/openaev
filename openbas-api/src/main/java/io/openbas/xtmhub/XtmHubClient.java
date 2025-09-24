package io.openbas.xtmhub;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.openbas.authorisation.HttpClientFactory;
import io.openbas.xtmhub.config.XtmHubConfig;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class XtmHubClient {
  private final XtmHubConfig config;
  private final HttpClientFactory httpClientFactory;

  public XtmHubConnectivityStatus refreshRegistrationStatus(
      String platformId, String platformVersion, String token) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(config.getApiUrl() + "/graphql-api");
      httpPost.addHeader("Content-Type", "application/json; charset=utf-8");
      httpPost.addHeader("Accept", "application/json");

      StringEntity httpBody = buildMutationBody(platformId, platformVersion, token);
      httpPost.setEntity(httpBody);
      return httpClient.execute(httpPost, this::parseResponseAsConnectivityStatus);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);

      return XtmHubConnectivityStatus.INACTIVE;
    }
  }

  @NotNull
  private StringEntity buildMutationBody(String platformId, String platformVersion, String token) {
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
              "token": "%s"
            }
          }
        }
        """,
            platformId, platformVersion, token);

    JsonElement element = JsonParser.parseString(mutationBody);
    return new StringEntity(element.toString());
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

      return XtmHubConnectivityStatus.INACTIVE;
    } catch (Exception e) {
      log.warn("Error occurred while parsing XTM Hub connectivity response: {}", e.getMessage(), e);

      return XtmHubConnectivityStatus.INACTIVE;
    }
  }
}
