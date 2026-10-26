package io.openaev.xtmhub;

import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
  private static final String PLATFORM_CONTRACT_FIELD = "platformContract";
  private static final String PLATFORM_CONTRACT_UNKNOWN_FIELD_ERROR =
      "Field \"" + PLATFORM_CONTRACT_FIELD + "\" is not defined by type";
  private final AtomicBoolean isPlatformContractFallbackLogged = new AtomicBoolean(false);
  private static final String CONSUME_NEWS_FEED_ITEMS_MUTATION =
      "mutation ConsumeProvisionedNewsFeedItems { consumeProvisionedNewsFeedItems {"
          + " news_feed_items { id title type tags metadata { key value } creation_date is_deleted }"
          + " available_news_feed_types } }";
  private static final ObjectMapper NEWS_FEED_MAPPER = new ObjectMapper();
  private static final String URL_PATH_METADATA_KEY = "url_path";
  // What the Hub answers when the platform id and token headers do not match a registration: the
  // error code when it sends one, else (older Hubs) a message containing one of these values.
  private static final List<String> TOKEN_REJECTED_CODES =
      List.of("PLATFORM_NOT_REGISTERED", "INVALID_PLATFORM_ID");
  private static final List<String> TOKEN_REJECTED_MESSAGES =
      List.of(
          "Invalid token provided",
          "Invalid platform token provided",
          "PLATFORM_NOT_REGISTERED",
          "INVALID_PLATFORM_ID");

  private record HubResponse(int code, String body) {}

  // What the Hub sends for a news feed item. The fields are not validated by Jackson: a missing
  // or malformed one only skips this item, see parseNewsFeedResponse.
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record HubNewsFeedItem(
      String id,
      String title,
      String type,
      List<String> tags,
      List<HubNewsFeedMetadata> metadata,
      @JsonProperty("creation_date") String creationDate,
      @JsonProperty("is_deleted") boolean deleted) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record HubNewsFeedMetadata(String key, String value) {}

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

  /** Consumes the news feed items the Hub provisioned for this platform, which removes them. */
  public NewsFeedFetchResult consumeProvisionedNewsFeedItems(String token, String platformId) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
      httpPost.addHeader(XTMHUB_PLATFORM_TOKEN_HEADER, token);
      httpPost.addHeader(XTMHUB_PLATFORM_ID_HEADER, platformId);
      JsonObject body = new JsonObject();
      body.addProperty("query", CONSUME_NEWS_FEED_ITEMS_MUTATION);
      httpPost.setEntity(new StringEntity(body.toString()));
      HubResponse response =
          httpClient.execute(
              httpPost,
              httpResponse ->
                  new HubResponse(
                      httpResponse.getCode(),
                      httpResponse.getEntity() == null
                          ? ""
                          : EntityUtils.toString(httpResponse.getEntity(), "UTF-8")));
      return parseNewsFeedResponse(response);
    } catch (Exception e) {
      log.warn("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage());
      return new NewsFeedFetchResult.Failed();
    }
  }

  public XtmHubConnectivityStatus refreshRegistrationStatusSingleTenant(
      String platformId,
      String platformVersion,
      String platformContract,
      String token,
      String url,
      String tenantId,
      String tenantName) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HubResponse response =
          postRefreshWithPlatformContract(
              httpClient,
              platformContract,
              contract ->
                  buildRefreshStatusSingleTenantBody(
                      platformId, platformVersion, contract, token, url, tenantId, tenantName));
      return parseResponseAsConnectivityStatus(response);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);
      return XtmHubConnectivityStatus.INACTIVE;
    }
  }

  public Map<String, XtmHubConnectivityStatus> refreshRegistrationStatusAllTenants(
      String platformId,
      String platformVersion,
      String platformContract,
      Map<String, TenantRegistrationDetails> tenants) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HubResponse response =
          postRefreshWithPlatformContract(
              httpClient,
              platformContract,
              contract ->
                  buildRefreshStatusAllTenantsBody(platformId, platformVersion, contract, tenants));
      return parseAllTenantsResponseAsConnectivityStatus(response);
    } catch (Exception e) {
      log.error("XTM Hub is unreachable on {}: {}", config.getApiUrl(), e.getMessage(), e);
      return Map.of();
    }
  }

  private HubResponse postRefreshWithPlatformContract(
      CloseableHttpClient httpClient,
      String platformContract,
      Function<String, StringEntity> bodyBuilder)
      throws IOException {
    HubResponse response = postRefresh(httpClient, bodyBuilder.apply(platformContract));
    if (platformContract == null || !isPlatformContractRejected(response)) {
      return response;
    }
    if (isPlatformContractFallbackLogged.compareAndSet(false, true)) {
      log.warn(
          "XTM Hub does not accept the platform edition yet, refreshing the connectivity without it");
    }
    return postRefresh(httpClient, bodyBuilder.apply(null));
  }

  private HubResponse postRefresh(CloseableHttpClient httpClient, StringEntity httpBody)
      throws IOException {
    HttpPost httpPost = new HttpPost(this.graphqlEndpoint);
    httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    httpPost.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
    httpPost.setEntity(httpBody);
    return httpClient.execute(
        httpPost,
        response ->
            new HubResponse(
                response.getCode(),
                response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity(), "UTF-8")));
  }

  // A hub older than the edition refresh rejects the field as unknown, in graphql-js's wording.
  // Any other error naming the field, an invalid value for instance, comes from a hub that knows
  // it, so the refresh is not sent again without it.
  private static boolean isPlatformContractRejected(HubResponse response) {
    try {
      JsonElement errors = JsonParser.parseString(response.body()).getAsJsonObject().get("errors");
      if (errors == null || !errors.isJsonArray()) {
        return false;
      }
      for (JsonElement error : errors.getAsJsonArray()) {
        JsonElement message = error.isJsonObject() ? error.getAsJsonObject().get("message") : null;
        if (message != null
            && message.isJsonPrimitive()
            && message.getAsString().contains(PLATFORM_CONTRACT_UNKNOWN_FIELD_ERROR)) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      return false;
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
      String tenantName,
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
              tenantName,
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

  @Nonnull
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

  @Nonnull
  private StringEntity buildRefreshStatusSingleTenantBody(
      String platformId,
      String platformVersion,
      String platformContract,
      String token,
      String url,
      String tenantId,
      String tenantName) {
    JsonObject input = new JsonObject();
    input.addProperty("platformId", platformId);
    input.addProperty("platformVersion", platformVersion);
    input.addProperty("token", token);
    input.addProperty("platformIdentifier", platformIdentifier);
    input.addProperty("url", url);
    input.addProperty("tenantId", tenantId);
    input.addProperty("tenantName", tenantName);
    if (platformContract != null) {
      input.addProperty(PLATFORM_CONTRACT_FIELD, platformContract);
    }

    JsonObject variables = new JsonObject();
    variables.add("input", input);

    JsonObject body = new JsonObject();
    body.addProperty(
        "query",
        "mutation refreshPlatformRegistrationConnectivityStatusSingleTenant($input: RefreshPlatformRegistrationConnectivityStatusSingleTenantInput!) { refreshPlatformRegistrationConnectivityStatusSingleTenant(input: $input) { status } }");
    body.add("variables", variables);

    return new StringEntity(body.toString());
  }

  @Nonnull
  private StringEntity buildRefreshStatusAllTenantsBody(
      String platformId,
      String platformVersion,
      String platformContract,
      Map<String, TenantRegistrationDetails> tenants) {

    JsonArray tenantsArray = new JsonArray();
    tenants.forEach(
        (tenantId, details) -> {
          JsonObject tenantToken = new JsonObject();
          tenantToken.addProperty("tenantId", tenantId);
          tenantToken.addProperty("tenantName", details.tenantName());
          tenantToken.addProperty("token", details.token());
          tenantToken.addProperty("url", details.url());
          tenantsArray.add(tenantToken);
        });

    JsonObject input = new JsonObject();
    input.addProperty("platformId", platformId);
    input.addProperty("platformVersion", platformVersion);
    input.addProperty("platformIdentifier", platformIdentifier);
    if (platformContract != null) {
      input.addProperty(PLATFORM_CONTRACT_FIELD, platformContract);
    }
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

  @Nonnull
  private StringEntity buildAutoRegisterBody(
      String platformContract,
      String platformId,
      String platformTitle,
      String platformUrl,
      String platformVersion,
      String tenantId,
      String tenantName,
      Long usersCount) {

    JsonObject platform = new JsonObject();
    platform.addProperty("contract", platformContract);
    platform.addProperty("id", platformId);
    platform.addProperty("title", platformTitle);
    platform.addProperty("url", platformUrl);
    platform.addProperty("version", platformVersion);
    platform.addProperty("tenantId", tenantId);
    platform.addProperty("tenantName", tenantName);

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

  private XtmHubConnectivityStatus parseResponseAsConnectivityStatus(HubResponse response) {
    if (response.code() != HttpStatus.SC_OK) {
      return XtmHubConnectivityStatus.INACTIVE;
    }
    try {
      JsonElement jsonResponse = JsonParser.parseString(response.body());
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
      HubResponse response) {
    if (response.code() != HttpStatus.SC_OK) {
      return Map.of();
    }
    try {
      JsonElement jsonResponse = JsonParser.parseString(response.body());
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

  private NewsFeedFetchResult parseNewsFeedResponse(HubResponse response) {
    if (response.code() == HttpStatus.SC_UNAUTHORIZED
        || response.code() == HttpStatus.SC_FORBIDDEN) {
      return new NewsFeedFetchResult.TokenRejected();
    }
    if (response.code() != HttpStatus.SC_OK) {
      log.warn("XTM Hub answered {} to the news feed items request", response.code());
      return new NewsFeedFetchResult.Failed();
    }
    try {
      JsonNode json = NEWS_FEED_MAPPER.readTree(response.body());
      if (json.has("errors")) {
        return parseNewsFeedErrors(json.get("errors"));
      }
      JsonNode data = json.path("data").path("consumeProvisionedNewsFeedItems");
      JsonNode hubItems = data.path("news_feed_items");
      if (!hubItems.isArray()) {
        throw new IllegalArgumentException("missing news_feed_items");
      }
      // The Hub removes the items it answers: a malformed one is skipped, not the whole answer.
      List<ProvisionedNewsFeedItem> items = new ArrayList<>();
      for (JsonNode hubItem : hubItems) {
        try {
          items.add(
              toProvisionedNewsFeedItem(
                  NEWS_FEED_MAPPER.treeToValue(hubItem, HubNewsFeedItem.class)));
        } catch (Exception e) {
          log.warn(
              "Skipping the malformed XTM Hub news feed item {}: {}",
              describeNewsFeedItem(hubItem),
              e.getMessage());
        }
      }
      return new NewsFeedFetchResult.Fetched(
          new ProvisionedNewsFeedItems(items, stringsOf(data.path("available_news_feed_types"))));
    } catch (Exception e) {
      log.warn("Error occurred while parsing XTM Hub news feed items response: {}", e.getMessage());
      return new NewsFeedFetchResult.Failed();
    }
  }

  private NewsFeedFetchResult parseNewsFeedErrors(JsonNode errors) {
    boolean tokenRejected =
        errors.isArray()
            && StreamSupport.stream(errors.spliterator(), false)
                .anyMatch(this::isTokenRejectedError);
    if (tokenRejected) {
      return new NewsFeedFetchResult.TokenRejected();
    }
    log.warn("XTM Hub answered errors to the news feed items request: {}", errors);
    return new NewsFeedFetchResult.Failed();
  }

  private boolean isTokenRejectedError(JsonNode error) {
    String code = textOrNull(error.path("extensions").path("code"));
    if (code != null && TOKEN_REJECTED_CODES.contains(code)) {
      return true;
    }
    String message = textOrNull(error.path("message"));
    return message != null && TOKEN_REJECTED_MESSAGES.stream().anyMatch(message::contains);
  }

  private ProvisionedNewsFeedItem toProvisionedNewsFeedItem(HubNewsFeedItem item) {
    String urlPath =
        item.metadata() == null
            ? null
            : item.metadata().stream()
                .filter(
                    metadata -> metadata != null && URL_PATH_METADATA_KEY.equals(metadata.key()))
                .findFirst()
                .map(HubNewsFeedMetadata::value)
                .orElse(null);
    return new ProvisionedNewsFeedItem(
        required(item.id(), "id"),
        required(item.title(), "title"),
        required(item.type(), "type"),
        item.tags() == null ? List.of() : item.tags().stream().filter(Objects::nonNull).toList(),
        urlPath,
        Instant.parse(required(item.creationDate(), "creation_date")),
        item.deleted());
  }

  private String describeNewsFeedItem(JsonNode hubItem) {
    String id = textOrNull(hubItem.path("id"));
    return id != null ? id : "(without id)";
  }

  private String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("missing field " + field);
    }
    return value;
  }

  private String textOrNull(JsonNode node) {
    return node.isValueNode() && !node.isNull() ? node.asText() : null;
  }

  private List<String> stringsOf(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    return StreamSupport.stream(node.spliterator(), false)
        .filter(JsonNode::isValueNode)
        .filter(element -> !element.isNull())
        .map(JsonNode::asText)
        .toList();
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
