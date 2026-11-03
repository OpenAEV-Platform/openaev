package io.openaev.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.engine.EngineVersionProbe.ServerInfo;
import io.openaev.exception.StartupException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Engine type detection from the engine root response")
class EngineTypeTest {

  private static final String ELASTICSEARCH_TAGLINE = "You Know, for Search";
  private static final String OPENSEARCH_TAGLINE =
      "The OpenSearch Project: https://opensearch.org/";

  /** Root answer of a self-managed Elasticsearch. */
  private static ServerInfo elasticsearch(String version) {
    return new ServerInfo(null, version, ELASTICSEARCH_TAGLINE, "default");
  }

  @Nested
  @DisplayName("Elasticsearch")
  class Elasticsearch {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
      "8.19.15, ELASTICSEARCH_8",
      "8.19.0-SNAPSHOT, ELASTICSEARCH_8",
      "8.15.0, ELASTICSEARCH_8",
      "9.0.0, ELASTICSEARCH_9",
      "9.5.1, ELASTICSEARCH_9",
      "10.0.0, ELASTICSEARCH_9",
    })
    @DisplayName("given an Elasticsearch version, should pick the client of its major")
    void given_elasticsearchVersion_should_pickClientOfItsMajor(
        String version, EngineType expected) {
      // Act
      EngineType type = EngineType.fromServerInfo(elasticsearch(version));

      // Assert
      assertEquals(expected, type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.17.0", "6.8.23"})
    @DisplayName("given an Elasticsearch older than 8, should refuse to start")
    void given_elasticsearchOlderThan8_should_refuseToStart(String version) {
      // Act
      StartupException exception =
          assertThrows(
              StartupException.class, () -> EngineType.fromServerInfo(elasticsearch(version)));

      // Assert
      assertEquals(
          "Unsupported Elasticsearch version "
              + version
              + ": Elasticsearch 8.19 or later is required",
          exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Elasticsearch Serverless")
  class Serverless {

    @ParameterizedTest
    @ValueSource(strings = {"8.11.0", "9.4.0"})
    @DisplayName(
        "given the serverless build flavor, should pick the latest client whatever the version")
    void given_serverlessBuildFlavor_should_pickLatestClient(String version) {
      // Arrange
      ServerInfo serverless =
          new ServerInfo(null, version, ELASTICSEARCH_TAGLINE, EngineType.SERVERLESS_BUILD_FLAVOR);

      // Act
      EngineType type = EngineType.fromServerInfo(serverless);

      // Assert
      assertEquals(EngineType.ELASTICSEARCH_9, type);
    }
  }

  @Nested
  @DisplayName("OpenSearch")
  class OpenSearch {

    @ParameterizedTest
    @ValueSource(strings = {"3.9.0", "2.19.1", "7.10.2"})
    @DisplayName("given the opensearch distribution, should pick OpenSearch whatever the version")
    void given_opensearchDistribution_should_pickOpenSearch(String version) {
      // Act
      EngineType type =
          EngineType.fromServerInfo(
              new ServerInfo(
                  EngineType.OPENSEARCH_DISTRIBUTION, version, OPENSEARCH_TAGLINE, null));

      // Assert
      assertEquals(EngineType.OPENSEARCH, type);
    }

    @Test
    @DisplayName(
        "given compatibility mode (no distribution, version 7.10.2), should pick OpenSearch from"
            + " its tagline")
    void given_compatibilityMode_should_pickOpenSearchFromTagline() {
      // Act
      EngineType type =
          EngineType.fromServerInfo(new ServerInfo(null, "7.10.2", OPENSEARCH_TAGLINE, null));

      // Assert
      assertEquals(EngineType.OPENSEARCH, type);
    }
  }

  @Nested
  @DisplayName("Unreadable answers")
  class Unreadable {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"latest", "x.1.0"})
    @DisplayName("given a missing or malformed version, should refuse to start")
    void given_malformedVersion_should_refuseToStart(String version) {
      // Act & Assert
      assertThrows(
          StartupException.class,
          () -> EngineType.fromServerInfo(new ServerInfo(null, version, null, null)));
    }

    @Test
    @DisplayName("given an unknown distribution, should refuse to start")
    void given_unknownDistribution_should_refuseToStart() {
      // Act
      StartupException exception =
          assertThrows(
              StartupException.class,
              () -> EngineType.fromServerInfo(new ServerInfo("solr", "9.0.0", null, null)));

      // Assert
      assertEquals(
          "Unsupported search engine distribution 'solr': only Elasticsearch and OpenSearch are"
              + " supported",
          exception.getMessage());
    }
  }

  @Nested
  @DisplayName("engine.client setting")
  class ClientSetting {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"auto", "AUTO", " auto "})
    @DisplayName("given auto or no value, should leave the choice to the detection")
    void given_autoOrNoValue_should_leaveChoiceToDetection(String client) {
      // Act & Assert
      assertTrue(EngineType.fromClient(client).isEmpty());
    }
  }
}
