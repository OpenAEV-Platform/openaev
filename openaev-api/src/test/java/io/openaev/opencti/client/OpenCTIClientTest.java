package io.openaev.opencti.client;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.client.response.fields.Error;
import io.openaev.utils.fixtures.opencti.MutationFixture;
import java.io.IOException;
import java.util.List;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
}
