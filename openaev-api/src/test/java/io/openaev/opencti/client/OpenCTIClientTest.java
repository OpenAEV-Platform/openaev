package io.openaev.opencti.client;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.client.response.fields.Error;
import io.openaev.utils.fixtures.opencti.MutationFixture;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class OpenCTIClientTest extends IntegrationTest {

  @MockitoBean private HttpClientFactory mockHttpClientFactory;
  @Mock private CloseableHttpClient mockHttpClient;
  @Autowired private OpenCTIClient client;

  // to set
  private final String baseUrl = "base_url";
  private final String authToken = "authToken";

  @BeforeEach
  public void setup() throws JsonProcessingException {
    when(mockHttpClientFactory.httpClientCustom()).thenReturn(mockHttpClient);
  }

  private OpenCTIClient.ExtractedData getMockResponse(int statusCode, String responseBody) {
    return new OpenCTIClient.ExtractedData(statusCode, responseBody);
  }

  @Nested
  @DisplayName("When calling execute")
  public class WhenCallingRegisterConnector {
    @Nested
    @DisplayName("When endpoint has a communication error")
    public class WhenEndpointHasACommunicationError {
      @BeforeEach
      public void setup() throws IOException {
        when(mockHttpClient.execute(
                (ClassicHttpRequest) any(), (HttpClientResponseHandler<?>) any()))
            .thenThrow(IOException.class);
      }

      @Test
      @DisplayName("It throws an exception")
      public void itThrowsAnException() {
        assertThatThrownBy(() -> client.execute(baseUrl, authToken, "fake mutation", null))
            .isInstanceOf(ClientProtocolException.class)
            .hasMessageContaining("Unexpected response for request on: %s".formatted(baseUrl))
            .hasCauseInstanceOf(IOException.class);
      }
    }

    @Nested
    @DisplayName("When endpoint returns NOK status")
    public class WhenEndpointReturnsNOKStatus {
      @BeforeEach
      public void setup() throws IOException {
        OpenCTIClient.ExtractedData mockResponse =
            getMockResponse(
                HttpStatus.SC_BAD_REQUEST,
                """
                {
                  "errors": [
                    {
                      "message": "it didnt go well"
                    }
                  ]
                }
                """);
        when(mockHttpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
            .thenReturn(mockResponse);
      }

      @Test
      @DisplayName("It returns the response as-is")
      public void itReturnsResponseAsIs() throws IOException {
        Response response = client.execute(baseUrl, authToken, "fake mutation", null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.isError()).isTrue();
        Error err = new Error();
        err.setMessage("it didnt go well");
        List<Error> expectedErrors = List.of(err);
        assertThat(response.getErrors()).isEqualTo(expectedErrors);
        assertThat(response.getData()).isNull();
      }
    }

    @Nested
    @DisplayName("When endpoint answers without a body")
    public class WhenEndpointAnswersWithoutABody {
      @Test
      @DisplayName("It returns the status of a blank body for the caller to classify")
      public void itReturnsTheStatusOfABlankBody() throws IOException {
        when(mockHttpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
            .thenReturn(getMockResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "  "));
        Response response = client.execute(baseUrl, authToken, "fake mutation", null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_SERVICE_UNAVAILABLE);
        assertThat(response.isError()).isTrue();
        assertThat(response.getErrors().getFirst().getMessage())
            .isEqualTo("Empty response body (HTTP 503)");
      }

      @Test
      @DisplayName("It returns the status of a response without entity")
      public void itReturnsTheStatusOfAResponseWithoutEntity() throws IOException {
        when(mockHttpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
            .thenAnswer(
                invocation ->
                    ((HttpClientResponseHandler<?>) invocation.getArgument(1))
                        .handleResponse(
                            new BasicClassicHttpResponse(HttpStatus.SC_TOO_MANY_REQUESTS)));
        Response response = client.execute(baseUrl, authToken, "fake mutation", null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_TOO_MANY_REQUESTS);
        assertThat(response.getErrors().getFirst().getMessage())
            .isEqualTo("Empty response body (HTTP 429)");
      }
    }

    @Nested
    @DisplayName("When endpoint returns non GraphQL standard response")
    public class WhenEndpointReturnsNonGraphQLStandardResponse {
      @Nested
      @DisplayName("With non JSON body")
      public class WithNonJsonBody {
        @BeforeEach
        public void setup() throws IOException {
          OpenCTIClient.ExtractedData mockResponse =
              getMockResponse(HttpStatus.SC_OK, "What's this ???");
          when(mockHttpClient.execute(
                  (ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
              .thenReturn(mockResponse);
        }

        @Test
        @DisplayName(
            "It returns a response stating that json parsing failed along with original body")
        public void itReturnsResponseAsIs() throws IOException {
          Response response = client.execute(baseUrl, authToken, "fake mutation", null);
          assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
          assertThat(response.isError()).isTrue();
          assertThat(response.getErrors().size()).isEqualTo(1);
          assertThat(response.getErrors().get(0).getMessage())
              .contains("Unrecognized token 'What'");
          assertThatJson(response.getData())
              .isEqualTo(
                  """
                          {
                            "response_body": "What's this ???"
                          }
                          """);
        }
      }

      @Nested
      @DisplayName("With JSON body")
      public class WithJsonBody {
        @BeforeEach
        public void setup() throws IOException {
          OpenCTIClient.ExtractedData mockResponse =
              getMockResponse(
                  HttpStatus.SC_OK,
                  """
                            {
                              "some_key": "some_value"
                            }
                            """);
          when(mockHttpClient.execute(
                  (ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
              .thenReturn(mockResponse);
        }

        @Test
        @DisplayName(
            "It returns a response stating that json parsing failed along with original body")
        public void itReturnsResponseAsIs() throws IOException {
          Response response = client.execute(baseUrl, authToken, "fake mutation", null);
          assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
          assertThat(response.isError()).isTrue();
          assertThatJson(response.getData())
              .isEqualTo(
                  """
                {"response_body":"{\\n  \\"some_key\\": \\"some_value\\"\\n}\\n"}
                """);
          assertThat(response.getErrors().size()).isEqualTo(1);
          assertThat(response.getErrors().get(0).getMessage())
              .contains("Response body does not conform to a GraphQL response.");
        }
      }
    }

    @Nested
    @DisplayName("When endpoint returns OK status")
    public class WhenEndpointReturnsOKStatus {
      @BeforeEach
      public void setup() throws IOException {
        OpenCTIClient.ExtractedData mockResponse =
            getMockResponse(
                HttpStatus.SC_OK,
                """
                {
                  "data": {
                    "outcome": "good"
                  }
                }
                """);
        when(mockHttpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
            .thenReturn(mockResponse);
      }

      @Test
      @DisplayName("It returns expected structure with separate text and variables")
      public void itReturnsExpectedStructureWithSeparateTextAndVariables() throws IOException {
        Response response = client.execute(baseUrl, authToken, "fake mutation", null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertThatJson(response.getData())
            .isEqualTo(
                """
          {
              "outcome": "good"
          }
          """);
        assertThat(response.getErrors().size()).isEqualTo(0);
        assertThat(response.isError()).isFalse();
      }

      @Test
      @DisplayName("It returns expected structure with opaque Mutation")
      public void itReturnsExpectedStructureWithOpaqueMutation() throws IOException {
        Response response =
            client.execute(baseUrl, authToken, MutationFixture.getDefaultMutation());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertThatJson(response.getData())
            .isEqualTo(
                """
                  {
                      "outcome": "good"
                  }
                  """);
        assertThat(response.getErrors().size()).isEqualTo(0);
        assertThat(response.isError()).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("When calling execute with a timeout")
  public class WhenCallingExecuteWithATimeout {
    private final CloseableHttpClient boundedHttpClient = mock(CloseableHttpClient.class);

    @BeforeEach
    public void setup() {
      when(mockHttpClientFactory.httpClientNoRetry(any(Timeout.class)))
          .thenReturn(boundedHttpClient);
    }

    @Test
    @DisplayName("It sends the request through a bounded client that never retries")
    public void itSendsTheRequestThroughABoundedClient() throws IOException {
      when(boundedHttpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler) any()))
          .thenReturn(getMockResponse(HttpStatus.SC_OK, "{\"data\": {\"outcome\": \"good\"}}"));

      Response response =
          client.execute(
              baseUrl, authToken, MutationFixture.getDefaultMutation(), Duration.ofSeconds(7));

      assertThat(response.isError()).isFalse();
      assertThatJson(response.getData()).isEqualTo("{\"outcome\": \"good\"}");
      ArgumentCaptor<Timeout> timeout = ArgumentCaptor.forClass(Timeout.class);
      verify(mockHttpClientFactory).httpClientNoRetry(timeout.capture());
      assertThat(timeout.getValue().toMilliseconds()).isEqualTo(7000L);
      verify(mockHttpClientFactory, never()).httpClientCustom();
    }

    @Test
    @DisplayName("It throws an exception when the endpoint cannot be reached")
    public void itThrowsWhenTheEndpointCannotBeReached() throws IOException {
      when(boundedHttpClient.execute(
              (ClassicHttpRequest) any(), (HttpClientResponseHandler<?>) any()))
          .thenThrow(IOException.class);

      assertThatThrownBy(
              () ->
                  client.execute(
                      baseUrl,
                      authToken,
                      MutationFixture.getDefaultMutation(),
                      Duration.ofSeconds(7)))
          .isInstanceOf(ClientProtocolException.class)
          .hasCauseInstanceOf(IOException.class);
    }
  }
}
