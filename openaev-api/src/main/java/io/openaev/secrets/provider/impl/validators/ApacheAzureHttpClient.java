package io.openaev.secrets.provider.impl.validators;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.hc.client5.http.classic.methods.ClassicHttpRequests;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Azure SDK {@link HttpClient} backed by an Apache {@link CloseableHttpClient}.
 *
 * <p>The SDK ships its own Netty transport, which knows nothing about the platform's extra trusted
 * certificates. Routing the SDK through the client built by {@code HttpClientFactory} keeps every
 * outbound call of the platform under the same TLS configuration.
 *
 * <p>Responses are buffered: the calls made through this client (token exchanges) answer with small
 * payloads, and buffering means no connection is ever left open behind a response the SDK forgot to
 * close.
 */
public class ApacheAzureHttpClient implements HttpClient {

  /** Computed by Apache from the entity: sending the SDK's own copy is rejected as a duplicate. */
  private static final Set<String> HEADERS_MANAGED_BY_APACHE =
      Set.of("content-length", "transfer-encoding", "host");

  private final CloseableHttpClient httpClient;

  public ApacheAzureHttpClient(CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Mono<HttpResponse> send(HttpRequest request) {
    return send(request, Context.NONE);
  }

  @Override
  public Mono<HttpResponse> send(HttpRequest request, Context context) {
    // The Apache client blocks: keep it off the reactor event loop.
    return Mono.fromCallable(() -> sendSync(request, context))
        .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public HttpResponse sendSync(HttpRequest request, Context context) {
    // The client is shared across tenants and Apache keeps cookies by default: a fresh store per
    // request stops the cookies Entra ID sets for one tenant from being replayed to another.
    HttpClientContext httpContext = HttpClientContext.create();
    httpContext.setCookieStore(new BasicCookieStore());
    try {
      return httpClient.execute(
          toApacheRequest(request),
          httpContext,
          response -> {
            HttpHeaders headers = new HttpHeaders();
            for (Header header : response.getHeaders()) {
              headers.add(header.getName(), header.getValue());
            }
            byte[] body =
                response.getEntity() == null
                    ? new byte[0]
                    : EntityUtils.toByteArray(response.getEntity());
            return new BufferedResponse(request, response.getCode(), headers, body);
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ClassicHttpRequest toApacheRequest(HttpRequest request) {
    ClassicHttpRequest apacheRequest;
    try {
      apacheRequest =
          ClassicHttpRequests.create(request.getHttpMethod().name(), request.getUrl().toURI());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid request URL", e);
    }
    request
        .getHeaders()
        .forEach(
            header -> {
              if (!HEADERS_MANAGED_BY_APACHE.contains(header.getName().toLowerCase())) {
                for (String value : header.getValuesList()) {
                  apacheRequest.addHeader(header.getName(), value);
                }
              }
            });
    BinaryData body = request.getBodyAsBinaryData();
    if (body != null) {
      // No content type on the entity: the SDK already put its own Content-Type header above.
      apacheRequest.setEntity(new ByteArrayEntity(body.toBytes(), null));
    }
    return apacheRequest;
  }

  /** A response fully read from the wire. */
  private static final class BufferedResponse extends HttpResponse {

    private final int statusCode;
    private final HttpHeaders headers;
    private final byte[] body;

    private BufferedResponse(
        HttpRequest request, int statusCode, HttpHeaders headers, byte[] body) {
      super(request);
      this.statusCode = statusCode;
      this.headers = headers;
      this.body = body;
    }

    @Override
    public int getStatusCode() {
      return statusCode;
    }

    @Override
    public String getHeaderValue(String name) {
      return headers.getValue(name);
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    @Override
    public Flux<ByteBuffer> getBody() {
      return Flux.just(ByteBuffer.wrap(body));
    }

    @Override
    public Mono<byte[]> getBodyAsByteArray() {
      return Mono.just(body);
    }

    @Override
    public Mono<String> getBodyAsString() {
      return getBodyAsString(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<String> getBodyAsString(Charset charset) {
      return Mono.just(new String(body, charset));
    }
  }
}
