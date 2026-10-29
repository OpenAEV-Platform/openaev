package io.openaev.xtmhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmhub.config.XtmHubConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("XtmHubClient unit tests")
class XtmHubClientTest {

  private static final String TOKEN = "platform-token";
  private static final String PLATFORM_ID = "platform-id";
  private static final String API_URL = "http://hub.test";

  @Mock private XtmHubConfig config;
  @Mock private HttpClientFactory httpClientFactory;
  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private CloseableHttpClient httpClient;

  @InjectMocks private XtmHubClient xtmHubClient;

  @BeforeEach
  void setUp() throws Exception {
    when(config.getApiUrl()).thenReturn(API_URL);
    when(httpClientFactory.httpClientNoRetry(XtmHubClient.NEWS_FEED_TIMEOUT))
        .thenReturn(httpClient);
    xtmHubClient.init();
  }

  @Nested
  @DisplayName("consumeProvisionedNewsFeedItems")
  class ConsumeProvisionedNewsFeedItems {

    @Test
    @DisplayName("Given a valid answer, should return the provisioned items and types")
    void given_validAnswer_should_returnFetchedItems() throws Exception {
      // Arrange
      givenHubAnswers(
          200,
          """
          {"data":{"consumeProvisionedNewsFeedItems":{
            "news_feed_items":[{
              "id":"hub-1","title":"Scenario","type":"RESOURCE_SCENARIO",
              "tags":["phishing"],
              "metadata":[{"key":"url_path","value":"/scenarios/hub-1"}],
              "creation_date":"2026-10-20T10:15:30Z","is_deleted":true}],
            "available_news_feed_types":["RESOURCE_SCENARIO"]}}}
          """);

      // Act
      NewsFeedFetchResult result = consume();

      // Assert
      assertThat(result)
          .isInstanceOfSatisfying(
              NewsFeedFetchResult.Fetched.class,
              fetched -> {
                assertThat(fetched.payload().availableTypes()).containsExactly("RESOURCE_SCENARIO");
                assertThat(fetched.payload().items())
                    .containsExactly(
                        new ProvisionedNewsFeedItem(
                            "hub-1",
                            "Scenario",
                            "RESOURCE_SCENARIO",
                            List.of("phishing"),
                            "/scenarios/hub-1",
                            Instant.parse("2026-10-20T10:15:30Z"),
                            true));
              });
    }

    @Test
    @DisplayName("Given a request, should use the client that never retries, with a timeout")
    void given_request_should_useNoRetryClientWithTimeout() throws Exception {
      // Arrange
      givenHubAnswers(200, emptyAnswer());

      // Act
      consume();

      // Assert
      verify(httpClientFactory).httpClientNoRetry(XtmHubClient.NEWS_FEED_TIMEOUT);
      verify(httpClientFactory, never()).httpClientCustom();
    }

    @Test
    @DisplayName("Given a request, should send the token and platform id headers")
    void given_request_should_sendTokenAndPlatformIdHeaders() throws Exception {
      // Arrange
      givenHubAnswers(200, emptyAnswer());

      // Act
      consume();

      // Assert
      ArgumentCaptor<ClassicHttpRequest> request =
          ArgumentCaptor.forClass(ClassicHttpRequest.class);
      verify(httpClient).execute(request.capture(), anyHandler());
      assertThat(request.getValue().getFirstHeader("XTM-Hub-Platform-Token").getValue())
          .isEqualTo(TOKEN);
      assertThat(request.getValue().getFirstHeader("XTM-Hub-Platform-Id").getValue())
          .isEqualTo(PLATFORM_ID);
    }

    @ParameterizedTest(name = "Given a {0} answer, should report the token as rejected")
    @ValueSource(ints = {401, 403})
    void given_unauthorizedAnswer_should_reportTokenRejected(int code) throws Exception {
      // Arrange
      givenHubAnswers(code, "");

      // Act & Assert
      assertThat(consume()).isInstanceOf(NewsFeedFetchResult.TokenRejected.class);
    }

    @Test
    @DisplayName("Given another HTTP error, should fail")
    void given_otherHttpError_should_fail() throws Exception {
      // Arrange
      givenHubAnswers(500, "");

      // Act & Assert
      assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
    }

    @Test
    @DisplayName("Given an answer without body, should fail")
    void given_answerWithoutBody_should_fail() throws Exception {
      // Arrange
      givenHubAnswersWithoutEntity(200);

      // Act & Assert
      assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
    }

    @Test
    @DisplayName("Given an unreachable Hub, should fail")
    void given_unreachableHub_should_fail() throws Exception {
      // Arrange
      when(httpClient.execute(any(ClassicHttpRequest.class), anyHandler()))
          .thenThrow(new IOException("connection refused"));

      // Act & Assert
      assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
    }

    @Test
    @DisplayName("Given an answer without news_feed_items, should fail")
    void given_answerWithoutNewsFeedItems_should_fail() throws Exception {
      // Arrange
      givenHubAnswers(200, "{\"data\":{\"consumeProvisionedNewsFeedItems\":{}}}");

      // Act & Assert
      assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
    }

    @Test
    @DisplayName("Given a body that is not JSON, should fail")
    void given_bodyNotJson_should_fail() throws Exception {
      // Arrange
      givenHubAnswers(200, "<html>bad gateway</html>");

      // Act & Assert
      assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
    }

    @Nested
    @DisplayName("GraphQL errors")
    class GraphQlErrors {

      @ParameterizedTest(name = "Given the error code {0}, should report the token as rejected")
      @ValueSource(strings = {"PLATFORM_NOT_REGISTERED", "INVALID_PLATFORM_ID"})
      void given_rejectedErrorCode_should_reportTokenRejected(String code) throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            "{\"errors\":[{\"message\":\"nope\",\"extensions\":{\"code\":\"" + code + "\"}}]}");

        // Act & Assert
        assertThat(consume()).isInstanceOf(NewsFeedFetchResult.TokenRejected.class);
      }

      @Test
      @DisplayName(
          "Given an unrelated code but a token message, should report the token as rejected")
      void given_unrelatedCodeAndTokenMessage_should_reportTokenRejected() throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            "{\"errors\":[{\"message\":\"Invalid platform token provided\","
                + "\"extensions\":{\"code\":\"OTHER\"}}]}");

        // Act & Assert
        assertThat(consume()).isInstanceOf(NewsFeedFetchResult.TokenRejected.class);
      }

      @Test
      @DisplayName("Given an unrelated error, should fail")
      void given_unrelatedError_should_fail() throws Exception {
        // Arrange
        givenHubAnswers(
            200, "{\"errors\":[{\"message\":\"boom\",\"extensions\":{\"code\":\"OTHER\"}}]}");

        // Act & Assert
        assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
      }

      @Test
      @DisplayName("Given an error without message nor code, should fail")
      void given_errorWithoutMessageNorCode_should_fail() throws Exception {
        // Arrange
        givenHubAnswers(200, "{\"errors\":[{}]}");

        // Act & Assert
        assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
      }

      @Test
      @DisplayName("Given errors that are not a list, should fail")
      void given_errorsNotAList_should_fail() throws Exception {
        // Arrange
        givenHubAnswers(200, "{\"errors\":{\"message\":\"Invalid token provided\"}}");

        // Act & Assert
        assertThat(consume()).isInstanceOf(NewsFeedFetchResult.Failed.class);
      }
    }

    @Nested
    @DisplayName("Item parsing")
    class ItemParsing {

      @Test
      @DisplayName("Given no tags nor metadata, should map to no tags and no url path")
      void given_noTagsNorMetadata_should_mapToEmptyValues() throws Exception {
        // Arrange
        givenHubAnswers(200, answerWithItems(item("hub-1", "\"tags\":null,\"metadata\":null")));

        // Act & Assert
        assertThat(fetchedItems())
            .singleElement()
            .satisfies(
                item -> {
                  assertThat(item.tags()).isEmpty();
                  assertThat(item.urlPath()).isNull();
                  assertThat(item.deleted()).isFalse();
                });
      }

      @Test
      @DisplayName("Given null tags and metadata of other keys, should ignore them")
      void given_nullTagsAndOtherMetadata_should_ignoreThem() throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            answerWithItems(
                item(
                    "hub-1",
                    "\"tags\":[null,\"phishing\"],"
                        + "\"metadata\":[null,{\"key\":\"other\",\"value\":\"x\"},"
                        + "{\"key\":\"url_path\",\"value\":\"/scenarios/1\"}]")));

        // Act & Assert
        assertThat(fetchedItems())
            .singleElement()
            .satisfies(
                item -> {
                  assertThat(item.tags()).containsExactly("phishing");
                  assertThat(item.urlPath()).isEqualTo("/scenarios/1");
                });
      }

      @Test
      @DisplayName("Given metadata without url path, should leave the url path empty")
      void given_metadataWithoutUrlPath_should_leaveUrlPathEmpty() throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            answerWithItems(
                item("hub-1", "\"tags\":[],\"metadata\":[{\"key\":\"other\",\"value\":\"x\"}]")));

        // Act & Assert
        assertThat(fetchedItems()).singleElement().satisfies(i -> assertThat(i.urlPath()).isNull());
      }

      @Test
      @DisplayName("Given malformed items, should skip them and keep the valid ones")
      void given_malformedItems_should_skipThemAndKeepValidOnes() throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            answerWithItems(
                item("hub-valid", "\"tags\":[]"),
                // missing id
                "{\"title\":\"t\",\"type\":\"RESOURCE_SCENARIO\","
                    + "\"creation_date\":\"2026-10-20T10:15:30Z\"}",
                // blank title
                "{\"id\":\"hub-blank\",\"title\":\" \",\"type\":\"RESOURCE_SCENARIO\","
                    + "\"creation_date\":\"2026-10-20T10:15:30Z\"}",
                // missing creation date
                "{\"id\":\"hub-no-date\",\"title\":\"t\",\"type\":\"RESOURCE_SCENARIO\"}",
                // unparsable creation date
                "{\"id\":\"hub-bad-date\",\"title\":\"t\",\"type\":\"RESOURCE_SCENARIO\","
                    + "\"creation_date\":\"yesterday\"}",
                // id that is not a text, then a non-object item
                "{\"id\":{\"nested\":true},\"title\":\"t\",\"type\":\"RESOURCE_SCENARIO\"}",
                "42"));

        // Act & Assert
        assertThat(fetchedItems())
            .extracting(ProvisionedNewsFeedItem::id)
            .containsExactly("hub-valid");
      }
    }

    @Nested
    @DisplayName("Available types")
    class AvailableTypes {

      @Test
      @DisplayName("Given a list mixing texts, nulls and objects, should keep the value nodes")
      void given_mixedList_should_keepValueNodes() throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            "{\"data\":{\"consumeProvisionedNewsFeedItems\":{\"news_feed_items\":[],"
                + "\"available_news_feed_types\":[\"RESOURCE_SCENARIO\",null,{\"a\":1},7]}}}");

        // Act & Assert
        assertThat(fetched().availableTypes()).containsExactly("RESOURCE_SCENARIO", "7");
      }

      @Test
      @DisplayName("Given types that are not a list, should return no type")
      void given_typesNotAList_should_returnNoType() throws Exception {
        // Arrange
        givenHubAnswers(
            200,
            "{\"data\":{\"consumeProvisionedNewsFeedItems\":{\"news_feed_items\":[],"
                + "\"available_news_feed_types\":\"RESOURCE_SCENARIO\"}}}");

        // Act & Assert
        assertThat(fetched().availableTypes()).isEmpty();
      }
    }
  }

  // -- Helpers --

  private NewsFeedFetchResult consume() {
    return xtmHubClient.consumeProvisionedNewsFeedItems(TOKEN, PLATFORM_ID);
  }

  private ProvisionedNewsFeedItems fetched() {
    NewsFeedFetchResult result = consume();
    assertThat(result).isInstanceOf(NewsFeedFetchResult.Fetched.class);
    return ((NewsFeedFetchResult.Fetched) result).payload();
  }

  private List<ProvisionedNewsFeedItem> fetchedItems() {
    return fetched().items();
  }

  private static String emptyAnswer() {
    return answerWithItems();
  }

  private static String answerWithItems(String... items) {
    return "{\"data\":{\"consumeProvisionedNewsFeedItems\":{\"news_feed_items\":["
        + String.join(",", items)
        + "],\"available_news_feed_types\":[\"RESOURCE_SCENARIO\"]}}}";
  }

  private static String item(String id, String extraFields) {
    return "{\"id\":\""
        + id
        + "\",\"title\":\"Title\",\"type\":\"RESOURCE_SCENARIO\","
        + "\"creation_date\":\"2026-10-20T10:15:30Z\","
        + extraFields
        + "}";
  }

  private static HttpClientResponseHandler<Object> anyHandler() {
    return any();
  }

  private void givenHubAnswers(int code, String body) throws IOException {
    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
    when(response.getCode()).thenReturn(code);
    when(response.getEntity()).thenReturn(new StringEntity(body, StandardCharsets.UTF_8));
    runHandlerOn(response);
  }

  private void givenHubAnswersWithoutEntity(int code) throws IOException {
    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
    when(response.getCode()).thenReturn(code);
    when(response.getEntity()).thenReturn(null);
    runHandlerOn(response);
  }

  private void runHandlerOn(ClassicHttpResponse response) throws IOException {
    when(httpClient.execute(any(HttpPost.class), anyHandler()))
        .thenAnswer(
            invocation -> {
              HttpClientResponseHandler<?> handler = invocation.getArgument(1);
              return handler.handleResponse(response);
            });
  }
}
