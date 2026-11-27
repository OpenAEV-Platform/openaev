package io.openaev.authorisation;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class HttpClientFactory {

  private final X509TrustManager trustManager;

  /** Create default httpClient for all the app with extra trusted certs */
  private CloseableHttpClient httpClientBuilder(
      boolean disableAutomaticRetries, Timeout connectionTimeout) {
    HttpClientBuilder custom = HttpClients.custom();
    if (disableAutomaticRetries) {
      custom.disableAutomaticRetries();
    }
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {trustManager}, null);
      TlsSocketStrategy tlsStrategy =
          ClientTlsStrategyBuilder.create().setSslContext(sslContext).buildClassic();
      return custom.setConnectionManager(connectionManager(tlsStrategy, connectionTimeout)).build();
    } catch (Exception e) {
      log.error("Unable to load the custom ssl context", e);
      if (connectionTimeout != null) {
        custom.setConnectionManager(connectionManager(null, connectionTimeout));
      }
      return custom.build();
    }
  }

  private static HttpClientConnectionManager connectionManager(
      TlsSocketStrategy tlsStrategy, Timeout connectionTimeout) {
    PoolingHttpClientConnectionManagerBuilder builder =
        PoolingHttpClientConnectionManagerBuilder.create();
    if (tlsStrategy != null) {
      builder.setTlsSocketStrategy(tlsStrategy);
    }
    if (connectionTimeout != null) {
      builder
          .setDefaultConnectionConfig(
              ConnectionConfig.custom()
                  .setConnectTimeout(connectionTimeout)
                  .setSocketTimeout(connectionTimeout)
                  .build())
          .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(connectionTimeout).build());
    }
    return builder.build();
  }

  /** Create default httpClient for all the app with extra trusted certs */
  public CloseableHttpClient httpClientCustom() {
    return httpClientBuilder(false, null);
  }

  /**
   * Disable automatic retries so the client does not honor server-provided Retry-After (e.g. on
   * HTTP 429) by sleeping for the full delay before retrying, which would otherwise make
   * synchronous calls appear stuck for hours.
   */
  public CloseableHttpClient httpClientNoRetry() {
    return httpClientBuilder(true, null);
  }

  /**
   * {@link #httpClientNoRetry()} whose connection is bounded too: the TCP connect, the TLS
   * handshake and every socket read give up after {@code connectionTimeout}, where the client
   * otherwise waits minutes on an unreachable host.
   */
  public CloseableHttpClient httpClientNoRetry(Timeout connectionTimeout) {
    return httpClientBuilder(true, connectionTimeout);
  }
}
