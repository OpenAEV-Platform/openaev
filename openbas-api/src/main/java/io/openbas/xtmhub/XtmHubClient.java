package io.openbas.xtmhub;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.openbas.authorisation.HttpClientFactory;
import io.openbas.xtmhub.config.XTMHubConfig;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class XtmHubClient {
  private final XTMHubConfig config;
  private final HttpClientFactory httpClientFactory;

  public XtmHubConnectivityStatus refreshRegistrationStatus(
      String platformId, String platformVersion, String token) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(config.getApiUrl() + "/graphql-api");
      httpPost.addHeader("Content-Type", "application/json; charset=utf-8");
      httpPost.addHeader("Accept", "application/json");

      StringEntity httpBody = buildMutationBody(platformId, token, platformVersion);
      httpPost.setEntity(httpBody);
      return httpClient.execute(
          httpPost,
          classicHttpResponse -> {
            if (classicHttpResponse.getCode() == HttpStatus.SC_OK) {
              return XtmHubConnectivityStatus.ACTIVE;
            } else {
              return XtmHubConnectivityStatus.INACTIVE;
            }
          });
    } catch (IOException e) {
      log.warn("XTM Hub is unreachable on {}", config.getApiUrl(), e);

      return XtmHubConnectivityStatus.INACTIVE;
    }
  }

  @NotNull
  private static StringEntity buildMutationBody(
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
              "token": "%s"
            }
          }
        }
        """,
            platformId, platformVersion, token);

    JsonElement element = JsonParser.parseString(mutationBody);
    return new StringEntity(element.toString());
  }
}
