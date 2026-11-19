package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.service.catalog_connectors.CatalogConnectorIngestionService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@DisplayName("Catalog connectors process tests")
@Transactional
public class CatalogConnectorIngestionServiceTest {
  private static final String REIMPORTED_SLUG = "reimported-injector";

  @Autowired private CatalogConnectorIngestionService catalogConnectorIngestionService;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;

  @Test
  @DisplayName("Should upload and return logo name when base64 is valid")
  void shouldUploadAndReturnLogoNameWhenBase64IsValid() throws Exception {

    String json =
        """
                        {
                          "title": "connector",
                          "description": "description",
                          "slug": "connector-validBase64",
                          "logo": "data:image/png;base64,aGVsbG8="
                        }
                        """;

    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    CatalogConnector connector = ingestion.buildCatalogConnector(contract);

    assertThat(connector.getLogoUrl()).isEqualTo("connector-validBase64-logo.png");
  }

  @Test
  @DisplayName("Should fallback to img-icon.png when base64 is invalid")
  void shouldFallbackToDefaultLogoWhenBase64Invalid() throws Exception {

    String json =
        """
                        {
                          "title": "connector",
                          "description": "description",
                          "slug": "connector-invalidBase64",
                          "logo": "data:image/png;base64,INVALID_BASE64"
                        }
                        """;

    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    CatalogConnector connector = ingestion.buildCatalogConnector(contract);

    assertThat(connector.getLogoUrl()).isEqualTo("img/icon-connector-default.png");
  }

  @Test
  @DisplayName("Should build catalog")
  void shouldBuildCatalogConnector() throws Exception {
    String json =
        """
                        {
                          "title": "connector",
                          "slug": "connector-slug",
                          "description": "Description",
                          "short_description": "short description",
                          "logo": "data:image/png;base64,xxx",
                          "use_cases": ["UC1", "UC2"],
                          "verified": true,
                          "last_verified_date": null,
                          "playbook_supported": false,
                          "max_confidence_level": 50,
                          "support_version": ">=5.5.4",
                          "subscription_link": null,
                          "source_code": "https://github.com/xxx",
                          "manager_supported": false,
                          "container_version": "rolling",
                          "container_image": "openaev/connector",
                          "container_type": "COLLECTOR"
                        }
                        """;
    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    CatalogConnector connector = ingestion.buildCatalogConnector(contract);

    assertThat(connector.getTitle()).isEqualTo("connector");
    assertThat(connector.getSlug()).isEqualTo("connector-slug");
    assertThat(connector.getDescription()).isEqualTo("Description");
    assertThat(connector.getShortDescription()).isEqualTo("short description");
    assertThat(connector.getUseCases()).containsExactlyInAnyOrder("UC1", "UC2");
    assertThat(connector.isVerified()).isTrue();
    assertThat(connector.getSupportVersion()).isEqualTo(">=5.5.4");
    assertThat(connector.getContainerImage()).isEqualTo("openaev/connector");

    assertThat(connector.getContainerType()).isEqualTo(ConnectorType.COLLECTOR);
  }

  @Test
  @DisplayName("Should read the injector contracts that have an id")
  void given_injectorContracts_should_readThoseWithAnId() throws Exception {
    // Arrange
    String json =
        """
        {
          "title": "connector",
          "slug": "connector-slug",
          "container_type": "INJECTOR",
          "injector_contracts": [
            {
              "contract_id": "contract-1",
              "contract_labels": {"en": "Host search", "fr": "Recherche d'hôtes"},
              "contract_domains": [{"domain_name": "Network", "domain_color": "#009933"}],
              "contract_platforms": ["Linux"],
              "contract_attack_patterns_external_ids": ["T1046"]
            },
            {"contract_labels": {"en": "No id"}}
          ]
        }
        """;
    JsonNode contract = new ObjectMapper().readTree(json);

    // Act
    CatalogConnector connector = catalogConnectorIngestionService.buildCatalogConnector(contract);

    // Assert
    assertThat(connector.getInjectorContracts())
        .singleElement()
        .satisfies(
            injectorContract -> {
              assertThat(injectorContract.getCatalogConnector()).isSameAs(connector);
              assertThat(injectorContract.getExternalId()).isEqualTo("contract-1");
              assertThat(injectorContract.getLabels())
                  .isEqualTo(Map.of("en", "Host search", "fr", "Recherche d'hôtes"));
              assertThat(injectorContract.getDomains()).containsExactly("Network");
              assertThat(injectorContract.getPlatforms()).containsExactly("Linux");
              assertThat(injectorContract.getAttackPatterns()).containsExactly("T1046");
            });
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("Should replace the stored injector contracts on re-import")
  void given_reimport_should_replaceInjectorContracts() throws Exception {
    try {
      // Arrange
      catalogConnectorIngestionService.extractCatalog(catalogWithContracts("first", "second"));

      // Act
      catalogConnectorIngestionService.extractCatalog(catalogWithContracts("second", "third"));

      // Assert
      CatalogConnector connector =
          catalogConnectorService
              .findBySlugWithConfigurationsAndInjectorContracts(REIMPORTED_SLUG)
              .orElseThrow();
      assertThat(connector.getInjectorContracts())
          .extracting(CatalogConnectorInjectorContract::getExternalId)
          .containsExactlyInAnyOrder("second", "third");
    } finally {
      catalogConnectorService
          .findBySlugWithConfigurationsAndInjectorContracts(REIMPORTED_SLUG)
          .ifPresent(connector -> catalogConnectorRepository.deleteById(connector.getId()));
    }
  }

  private static JsonNode catalogWithContracts(String... contractIds) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode connector =
        mapper
            .createObjectNode()
            .put("title", REIMPORTED_SLUG)
            .put("slug", REIMPORTED_SLUG)
            .put("container_type", "INJECTOR");
    ArrayNode contracts = connector.putArray("injector_contracts");
    for (String contractId : contractIds) {
      contracts.addObject().put("contract_id", contractId);
    }
    ObjectNode catalog = mapper.createObjectNode();
    catalog.putArray("contracts").add(connector);
    return catalog;
  }

  @Test
  @DisplayName("Should ignore use_cases when not an array")
  void shouldIgnoreUseCasesIfNotArray() throws Exception {
    String json =
        """
                        {
                          "title": "connector",
                          "slug": "connector-slug",
                          "use_cases": "not-an-array"
                        }
                        """;
    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    CatalogConnector connector = ingestion.buildCatalogConnector(contract);

    assertThat(connector.getUseCases()).isEmpty();
  }

  @Test
  @DisplayName("Should ignore connector type when not an enum")
  void shouldIgnoreConnectorTypeIfNotEnum() throws Exception {
    String json =
        """
                        {
                          "title": "connector",
                          "slug": "connector-slug",
                          "container_type": "INVALID"
                        }
                        """;

    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    CatalogConnector connector = ingestion.buildCatalogConnector(contract);

    assertThat(connector.getContainerType()).isNull();
  }

  @Test
  @DisplayName("Should return empty set when config_schema is missing")
  void shouldReturnEmptyWhenSchemaMissing() throws Exception {
    String json =
        """
                        {
                          "title": "connector",
                          "slug": "connector-slug"
                        }
                        """;

    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnector connector = new CatalogConnector();

    connector.setCatalogConnectorConfigurations(new HashSet<>());

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    Set<CatalogConnectorConfiguration> result =
        ingestion.buildConnectorConfigurations(contract, connector);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should return empty set when properties is missing")
  void shouldReturnEmptyWhenPropertiesMissing() throws Exception {
    String json =
        """
                        {
                          "title": "connector",
                          "slug": "connector-slug",
                            "config_schema": {
                                  "required": ["api_key"]
                                }
                        }
                        """;

    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnector connector = new CatalogConnector();

    connector.setCatalogConnectorConfigurations(new HashSet<>());

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    Set<CatalogConnectorConfiguration> result =
        ingestion.buildConnectorConfigurations(contract, connector);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should build complete connector configuration")
  void shouldBuildCompleteConfiguration() throws Exception {
    String json =
        """
                        {
                          "title": "connector",
                          "slug": "connector-slug",
                          "config_schema": {
                                "properties": {
                                  "api_key": {
                                    "description": "API key",
                                    "type": "string",
                                    "format": "password",
                                    "default": "demo",
                                    "enum": ["demo", "prod"],
                                    "writeOnly": true
                                  }
                                },
                                "required": ["api_key"]
                              }
                        }
                        """;

    JsonNode contract = new ObjectMapper().readTree(json);

    CatalogConnector connector = new CatalogConnector();

    connector.setCatalogConnectorConfigurations(new HashSet<>());

    CatalogConnectorIngestionService ingestion = catalogConnectorIngestionService;

    Set<CatalogConnectorConfiguration> result =
        ingestion.buildConnectorConfigurations(contract, connector);

    assertThat(result).hasSize(1);
    CatalogConnectorConfiguration conf = result.stream().findFirst().orElseThrow();

    assertThat(conf.getConnectorConfigurationKey()).isEqualTo("api_key");
    assertThat(conf.getConnectorConfigurationDescription()).isEqualTo("API key");
    assertThat(conf.getConnectorConfigurationType())
        .isEqualTo(CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING);
    assertThat(conf.getConnectorConfigurationFormat())
        .isEqualTo(CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD);
    assertThat(conf.getConnectorConfigurationDefault().asText()).isEqualTo("demo");
    assertThat(conf.getConnectorConfigurationEnum()).containsExactlyInAnyOrder("demo", "prod");
    assertThat(conf.isConnectorConfigurationRequired()).isTrue();
    assertThat(conf.isConnectorConfigurationWriteOnly()).isTrue();
  }
}
