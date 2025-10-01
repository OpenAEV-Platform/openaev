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

@Slf4j
public class RabbitMQHelper {

  private static String rabbitMQVersion;

  /**
   * Return the version of Rabbit MQ we're using
   *
   * @return the rabbit MQ version
   */
  public static String getRabbitMQVersion(RabbitmqConfig rabbitmqConfig) {
    // If we already have the version, we don't need to get it again
    if (rabbitMQVersion == null && rabbitmqConfig.getHostname() != null) {
      // Init the rabbit MQ management api overview url
      String uri =
          "http://"
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
