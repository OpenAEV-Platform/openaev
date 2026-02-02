package io.openaev.helper;

import io.openaev.config.RabbitmqConfig;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Collections;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Helper class for RabbitMQ operations and management API interactions.
 *
 * <p>Provides utility methods for querying RabbitMQ server information through its management API,
 * with support for SSL/TLS connections and insecure certificates.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
@Slf4j
public final class RabbitMQHelper {

  private RabbitMQHelper() {}

  /** Cached RabbitMQ version (thread-safe lazy initialization). */
  private static volatile String rabbitMQVersion;

  /** Lock object for thread-safe version initialization. */
  private static final Object VERSION_LOCK = new Object();

  /**
   * Retrieves the RabbitMQ server version via the management API.
   *
   * <p>Uses double-checked locking for thread-safe lazy initialization. The version is cached after
   * the first successful retrieval. Supports both SSL and non-SSL connections, with optional
   * certificate validation bypass.
   *
   * @param rabbitmqConfig the RabbitMQ configuration containing connection details
   * @return the RabbitMQ version string, or null if the version cannot be retrieved
   */
  public static String getRabbitMQVersion(RabbitmqConfig rabbitmqConfig) {
    // Double-checked locking for thread-safe lazy initialization
    if (rabbitMQVersion == null && rabbitmqConfig.getHostname() != null) {
      synchronized (VERSION_LOCK) {
        if (rabbitMQVersion != null) {
          return rabbitMQVersion;
        }

        // Init the rabbit MQ management api overview url
        String protocol = rabbitmqConfig.isSsl() ? "https://" : "http://";
        String uri =
            protocol
                + rabbitmqConfig.getHostname()
                + ":"
                + rabbitmqConfig.getManagementPort()
                + "/api/overview";

        RestTemplate restTemplate;
        try {
          restTemplate = rabbitMQRestTemplate(rabbitmqConfig);
        } catch (KeyStoreException
            | NoSuchAlgorithmException
            | KeyManagementException
            | CertificateException
            | IOException e) {
          log.error(e.getMessage(), e);
          return null;
        }

        // Init the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(rabbitmqConfig.getUser(), rabbitmqConfig.getPass());
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

        // Make the call
        ResponseEntity<?> result;
        try {
          result = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
        } catch (RestClientException e) {
          log.error(e.getMessage(), e);
          return null;
        }

        // Init the parser to get the rabbit_mq version
        BasicJsonParser jsonParser = new BasicJsonParser();
        rabbitMQVersion =
            (String) jsonParser.parseMap((String) result.getBody()).get("rabbitmq_version");
      }
    }

    return rabbitMQVersion;
  }

  private static RestTemplate rabbitMQRestTemplate(RabbitmqConfig rabbitmqConfig)
      throws KeyStoreException,
          NoSuchAlgorithmException,
          KeyManagementException,
          IOException,
          CertificateException {
    RestTemplate restTemplate =
        new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(2))
            .build();

    if (rabbitmqConfig.isSsl() && rabbitmqConfig.isManagementInsecure()) {
      HttpComponentsClientHttpRequestFactory requestFactoryHttp =
          new HttpComponentsClientHttpRequestFactory();

      TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
      SSLContext sslContext =
          SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
      SSLConnectionSocketFactory sslsf =
          new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
      Registry<ConnectionSocketFactory> socketFactoryRegistry =
          RegistryBuilder.<ConnectionSocketFactory>create()
              .register("https", sslsf)
              .register("http", new PlainConnectionSocketFactory())
              .build();

      BasicHttpClientConnectionManager connectionManager =
          new BasicHttpClientConnectionManager(socketFactoryRegistry);
      CloseableHttpClient httpClient =
          HttpClients.custom().setConnectionManager(connectionManager).build();
      requestFactoryHttp.setHttpClient(httpClient);
      restTemplate = new RestTemplate(requestFactoryHttp);
    } else if (rabbitmqConfig.isSsl()) {
      SSLContext sslContext =
          new SSLContextBuilder()
              .loadTrustMaterial(
                  rabbitmqConfig.getTrustStore().getURL(),
                  rabbitmqConfig.getTrustStorePassword().toCharArray())
              .build();
      SSLConnectionSocketFactory sslConFactory = new SSLConnectionSocketFactory(sslContext);
      HttpClientConnectionManager cm =
          PoolingHttpClientConnectionManagerBuilder.create()
              .setSSLSocketFactory(sslConFactory)
              .build();
      CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
      ClientHttpRequestFactory requestFactory =
          new HttpComponentsClientHttpRequestFactory(httpClient);
      restTemplate = new RestTemplate(requestFactory);
    }

    return restTemplate;
  }
}
