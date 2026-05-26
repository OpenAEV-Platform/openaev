package io.openaev.authorisation;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class HttpClientFactory {

  private final X509TrustManager trustManager;

  /** Create default httpClient for all the app with extra trusted certs */
  private CloseableHttpClient httpClientBuilder(boolean disableAutomaticRetries) {
    HttpClientBuilder custom = HttpClients.custom();
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {trustManager}, null);
      TlsSocketStrategy tlsStrategy =
          ClientTlsStrategyBuilder.create().setSslContext(sslContext).buildClassic();
      HttpClientConnectionManager cm =
          PoolingHttpClientConnectionManagerBuilder.create()
              .setTlsSocketStrategy(tlsStrategy)
              .build();
      HttpClientBuilder httpClientBuilder = custom.setConnectionManager(cm);
      if (disableAutomaticRetries) {
        httpClientBuilder.disableAutomaticRetries();
      }
      return httpClientBuilder.build();
    } catch (Exception e) {
      log.error("Unable to load the custom ssl context", e);
      if (disableAutomaticRetries) {
        custom.disableAutomaticRetries();
      }
      return custom.build();
    }
  }

  /** Create default httpClient for all the app with extra trusted certs */
  public CloseableHttpClient httpClientCustom() {
    return httpClientBuilder(false);
  }

  /**
   * Disable automatic retries so the client does not honor server-provided Retry-After (e.g. on
   * HTTP 429) by sleeping for the full delay before retrying, which would otherwise make
   * synchronous calls appear stuck for hours.
   */
  public CloseableHttpClient httpClientNoRetry() {
    return httpClientBuilder(true);
  }
}
