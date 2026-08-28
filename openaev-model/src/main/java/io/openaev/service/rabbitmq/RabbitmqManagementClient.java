package io.openaev.service.rabbitmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.RabbitmqConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HTTP client for the RabbitMQ Management API.
 *
 * <p>Single responsibility: query the broker via its HTTP management interface. This class does not
 * interact with AMQP channels or connections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitmqManagementClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RabbitmqConfig rabbitmqConfig;
  private final HttpClient rabbitmqManagementHttpClient;

  /**
   * Lists all queue names that start with the given prefix.
   *
   * @param prefix the prefix to filter queue names
   * @return a list of matching queue names
   * @throws IllegalStateException if the management API is unreachable or returns an error
   */
  public List<String> listQueueNamesWithPrefix(String prefix) {
    return listNamesFromManagementApi("queues", prefix);
  }

  /**
   * Lists all exchange names that start with the given prefix.
   *
   * @param prefix the prefix to filter exchange names
   * @return a list of matching exchange names
   * @throws IllegalStateException if the management API is unreachable or returns an error
   */
  public List<String> listExchangeNamesWithPrefix(String prefix) {
    return listNamesFromManagementApi("exchanges", prefix);
  }

  /**
   * Queries the RabbitMQ management HTTP API for a list of resources (queues or exchanges) and
   * returns names matching the given prefix.
   *
   * @throws IllegalStateException if the API is unreachable or returns a non-200 status
   */
  private List<String> listNamesFromManagementApi(String resource, String prefix) {
    List<String> result = new ArrayList<>();
    try {
      String scheme = rabbitmqConfig.isSsl() ? "https" : "http";
      String encodedVhost = URLEncoder.encode(rabbitmqConfig.getVhost(), StandardCharsets.UTF_8);
      URI uri =
          URI.create(
              scheme
                  + "://"
                  + rabbitmqConfig.getHostname()
                  + ":"
                  + rabbitmqConfig.getManagementPort()
                  + "/api/"
                  + resource
                  + "/"
                  + encodedVhost
                  + "?columns=name");

      String credentials = rabbitmqConfig.getUser() + ":" + rabbitmqConfig.getPass();
      String authHeader =
          "Basic "
              + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

      HttpRequest request =
          HttpRequest.newBuilder().uri(uri).header("Authorization", authHeader).GET().build();

      HttpResponse<String> response =
          rabbitmqManagementHttpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode root = MAPPER.readTree(response.body());
        for (JsonNode node : root) {
          String name = node.path("name").asText("");
          if (name.startsWith(prefix)) {
            result.add(name);
          }
        }
      } else {
        throw new IllegalStateException(
            "RabbitMQ management API returned status "
                + response.statusCode()
                + " for "
                + resource);
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Could not list " + resource + " from RabbitMQ management API: " + e.getMessage(), e);
    }

    return result;
  }
}
