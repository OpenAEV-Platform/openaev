package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.service.catalog_connectors.CatalogConnectorIngestionService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@DisplayName("Catalog connectors process tests")
@Transactional
public class CatalogConnectorIngestionServiceTest {
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private FileService fileService;
  @Autowired private CatalogConnectorIngestionService catalogConnectorIngestionService;
  @Autowired private ConnectorInstanceService connectorInstanceService;

  @Autowired
  private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  @Test
  @DisplayName("Should run ingestion catalog")
  public void shouldRunIngestionCatalog() throws Exception {
    String mockJson =
        """
                        {
                          "id": "filigran-catalog-id",
                          "name": "OpenAEV catalog",
                          "version": "rolling",
                          "contracts": [
                            {
                              "title": "contract1",
                              "slug": "contract-slug",
                              "description": "contract description",
                              "short_description": "contract short description",
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
                              "container_image": "openaev/connector-cpe",
                              "container_type": "COLLECTOR"
                            }
                          ]
                        }
                        """;

    ObjectMapper mapper = new ObjectMapper();

    JsonNode root = mapper.readTree(mockJson);

    List<CatalogConnector> result = catalogConnectorIngestionService.extractCatalog(root);

    assertThat(result).isNotEmpty();
    assertThat(result).hasSize(1);
  }

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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

    CatalogConnectorIngestionService ingestion =
        new CatalogConnectorIngestionService(
            catalogConnectorService,
            fileService,
            connectorInstanceService,
            connectorInstanceConfigurationRepository);

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
