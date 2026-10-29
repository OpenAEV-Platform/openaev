package io.openaev.authorisation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EnvironmentProxyRoutePlanner tests")
class EnvironmentProxyRoutePlannerTest {

  private static EnvironmentProxyRoutePlanner plannerWith(Map<String, String> environment) {
    UnaryOperator<String> lookup = environment::get;
    return new EnvironmentProxyRoutePlanner(lookup);
  }

  private static HttpHost proxyFor(EnvironmentProxyRoutePlanner planner, HttpHost target)
      throws Exception {
    return planner.determineRoute(target, HttpClientContext.create()).getProxyHost();
  }

  @Nested
  @DisplayName("Proxy selection")
  class ProxySelection {

    @Test
    @DisplayName("An https target goes through HTTPS_PROXY")
    void given_httpsProxy_should_routeHttpsTargetThroughIt() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner =
          plannerWith(Map.of("HTTPS_PROXY", "http://proxy.example.test:8080"));

      // Act
      HttpHost proxy = proxyFor(planner, new HttpHost("https", "login.example.test", 443));

      // Assert
      assertThat(proxy).isNotNull();
      assertThat(proxy.getHostName()).isEqualTo("proxy.example.test");
      assertThat(proxy.getPort()).isEqualTo(8080);
    }

    @Test
    @DisplayName("An http target goes through HTTP_PROXY, not HTTPS_PROXY")
    void given_httpTarget_should_useHttpProxy() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner =
          plannerWith(
              Map.of(
                  "HTTPS_PROXY", "http://secure-proxy.example.test:8443",
                  "HTTP_PROXY", "http://plain-proxy.example.test:3128"));

      // Act
      HttpHost proxy = proxyFor(planner, new HttpHost("http", "login.example.test", 80));

      // Assert
      assertThat(proxy.getHostName()).isEqualTo("plain-proxy.example.test");
    }

    @Test
    @DisplayName("A proxy given without a scheme is read as http")
    void given_proxyWithoutScheme_should_defaultToHttp() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner =
          plannerWith(Map.of("https_proxy", "proxy.example.test:3128"));

      // Act
      HttpHost proxy = proxyFor(planner, new HttpHost("https", "login.example.test", 443));

      // Assert
      assertThat(proxy.getSchemeName()).isEqualTo("http");
      assertThat(proxy.getPort()).isEqualTo(3128);
    }

    @Test
    @DisplayName("Without any proxy variable the request goes direct")
    void given_noVariable_should_goDirect() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner = plannerWith(Map.of());

      // Act
      HttpHost proxy = proxyFor(planner, new HttpHost("https", "login.example.test", 443));

      // Assert
      assertThat(proxy).isNull();
    }
  }

  @Nested
  @DisplayName("NO_PROXY")
  class NoProxy {

    private final Map<String, String> environment =
        Map.of(
            "HTTPS_PROXY", "http://proxy.example.test:8080",
            "NO_PROXY", "internal.test, .corp.test");

    @Test
    @DisplayName("A listed host and its subdomains bypass the proxy")
    void given_listedHost_should_goDirect() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner = plannerWith(environment);

      // Act / Assert
      assertThat(proxyFor(planner, new HttpHost("https", "internal.test", 443))).isNull();
      assertThat(proxyFor(planner, new HttpHost("https", "api.internal.test", 443))).isNull();
      assertThat(proxyFor(planner, new HttpHost("https", "login.corp.test", 443))).isNull();
    }

    @Test
    @DisplayName("A host that merely ends with a listed name is still proxied")
    void given_lookalikeHost_should_stillUseTheProxy() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner = plannerWith(environment);

      // Act
      HttpHost proxy = proxyFor(planner, new HttpHost("https", "notinternal.test", 443));

      // Assert
      assertThat(proxy).isNotNull();
    }

    @Test
    @DisplayName("A wildcard bypasses the proxy for every host")
    void given_wildcard_should_goDirect() throws Exception {
      // Arrange
      EnvironmentProxyRoutePlanner planner =
          plannerWith(Map.of("HTTPS_PROXY", "http://proxy.example.test:8080", "NO_PROXY", "*"));

      // Act
      HttpHost proxy = proxyFor(planner, new HttpHost("https", "login.example.test", 443));

      // Assert
      assertThat(proxy).isNull();
    }
  }
}
