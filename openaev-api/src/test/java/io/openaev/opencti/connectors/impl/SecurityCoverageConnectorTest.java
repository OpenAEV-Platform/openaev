package io.openaev.opencti.connectors.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.api.stix_process.StixApi;
import io.openaev.config.OpenAEVConfig;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import io.openaev.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

public class SecurityCoverageConnectorTest extends IntegrationTest {
  @Nested
  @DisplayName("Remote URL override")
  public class RemoteUrlOverride {
    @Nested
    @WithMockSecurityCoverageConnectorConfig(url = "https://opencti")
    @SpringBootTest
    @TestExecutionListeners(
        value = {RabbitMQTestListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
    @DisplayName("With only OpenCTI URL defined as FQDN")
    public class WithOnlyOpenCTIURLDefinedAsFQDN {
      @Autowired private SecurityCoverageConnector connector;

      @Test
      @DisplayName("it appends the graphql endpoint to the url")
      public void itAppendsTheGraphQLEndpointToTheURL() {
        assertThat(connector.getApiUrl()).isEqualTo("https://opencti/graphql");
      }
    }

    @Nested
    @WithMockSecurityCoverageConnectorConfig(url = "https://opencti/")
    @SpringBootTest
    @TestExecutionListeners(
        value = {RabbitMQTestListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
    @DisplayName("With only OpenCTI URL defined as FQDN with trailing slash")
    public class WithOnlyOpenCTIURLDefinedAsFQDNWithTrailingSlash {
      @Autowired private SecurityCoverageConnector connector;

      @Test
      @DisplayName("it appends the graphql endpoint to the url")
      public void itAppendsTheGraphQLEndpointToTheURL() {
        assertThat(connector.getApiUrl()).isEqualTo("https://opencti/graphql");
      }
    }

    @Nested
    @WithMockSecurityCoverageConnectorConfig(url = "https://opencti/graphql")
    @SpringBootTest
    @TestExecutionListeners(
        value = {RabbitMQTestListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
    @DisplayName("With only OpenCTI URL defined as FQDN with graphql endpoint set")
    public class WithOnlyOpenCTIURLDefinedAsFQDNWithGraphqlEndpointSet {
      @Autowired private SecurityCoverageConnector connector;

      @Test
      @DisplayName("it appends the graphql endpoint to the url")
      public void itAppendsTheGraphQLEndpointToTheURL() {
        assertThat(connector.getApiUrl()).isEqualTo("https://opencti/graphql");
      }
    }

    @Nested
    @WithMockSecurityCoverageConnectorConfig(url = "")
    @SpringBootTest
    @TestExecutionListeners(
        value = {RabbitMQTestListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
    @DisplayName("With OpenCTI URL not defined")
    public class WithNullUrl {
      @Autowired private SecurityCoverageConnector connector;

      @Test
      @DisplayName("it is null")
      public void itAppendsTheGraphQLEndpointToTheURL() {
        assertThat(connector.getApiUrl()).isNull();
      }
    }
  }

  @Nested
  @DisplayName("Listen Callback URI override")
  public class ListenCallbackURIOverride {
    @Nested
    @SpringBootTest
    @TestExecutionListeners(
        value = {RabbitMQTestListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
    @WithMockSecurityCoverageConnectorConfig(listenCallbackURI = "some_url")
    @DisplayName("When listen callback URI is set")
    public class WhenListenCallbackURIIsSet {
      @Autowired private OpenAEVConfig mainConfig;
      @Autowired private SecurityCoverageConnector connector;

      @Test
      @DisplayName("it ignores and overrides it")
      public void itIgnoresAndOverridesIt() {
        assertThat(connector.getListenCallbackURI())
            .isEqualTo(mainConfig.getBaseUrl() + StixApi.STIX_URI + "/process-bundle");
      }
    }

    @Nested
    @SpringBootTest
    @TestExecutionListeners(
        value = {RabbitMQTestListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
    @WithMockSecurityCoverageConnectorConfig
    @DisplayName("When listen callback URI is NOT set")
    public class WhenListenCallbackURIIsNOTSet {
      @Autowired private OpenAEVConfig mainConfig;
      @Autowired private SecurityCoverageConnector connector;

      @Test
      @DisplayName("it has the expected value")
      public void itIgnoresAndOverridesIt() {
        assertThat(connector.getListenCallbackURI())
            .isEqualTo(mainConfig.getBaseUrl() + StixApi.STIX_URI + "/process-bundle");
      }
    }
  }
}
