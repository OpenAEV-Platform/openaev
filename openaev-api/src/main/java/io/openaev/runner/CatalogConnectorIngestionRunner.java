package io.openaev.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.service.CatalogConnectorService;
import io.openaev.service.FileService;
import io.openaev.utils.TimeUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogConnectorIngestionRunner implements CommandLineRunner {
  private final CatalogConnectorService catalogConnectorService;
  private static final ObjectMapper mapper = new ObjectMapper();
  private final FileService fileService;
  private static final String resourcePath = "/catalog/catalog-integrators.json";

  @Override
  public void run(String... args) {

    try (InputStream is = CatalogConnectorIngestionRunner.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("File not found : " + resourcePath);
      }

      JsonNode rootNode = mapper.readTree(is);

      extractCatalog(rootNode);

    } catch (IOException e) {
      log.error("Error while reading file : {}", e.getMessage());
    }
  }

  List<CatalogConnector> extractCatalog(JsonNode rootNode) {
    JsonNode contracts = rootNode.get("contracts");
    if (contracts == null) {
      throw new IllegalArgumentException("contracts is null");
    }

    List<CatalogConnector> catalogConnectorList = new ArrayList<>();

    for (JsonNode contract : contracts) {
      CatalogConnector catalogConnector = buildCatalogConnector(contract);
      catalogConnectorList.add(catalogConnector);
    }

    return catalogConnectorService.saveAll(catalogConnectorList);
  }

  CatalogConnector buildCatalogConnector(JsonNode contract) {

    CatalogConnector connector =
        catalogConnectorService
            .findBySlug(contract.path("slug").asText())
            .orElseGet(CatalogConnector::new);

    List<String> useCases = new ArrayList<>();
    JsonNode arrUseCases = contract.path("use_cases");
    if (arrUseCases != null && arrUseCases.isArray()) {
      for (JsonNode uc : arrUseCases) {
        useCases.add(uc.asText());
      }
    }
    connector.setUseCases(new HashSet<>(useCases));

    connector.setTitle(contract.path("title").asText());
    connector.setSlug(contract.path("slug").asText());
    connector.setDescription(contract.path("description").asText());
    connector.setShortDescription(contract.path("short_description").asText());
    String base64Logo = contract.path("logo").asText(null);
    if (base64Logo != null && !base64Logo.isBlank()) {
      String logoPath = uploadBase64Image(base64Logo, contract.path("slug").asText());
      connector.setLogoUrl(logoPath);
    }

    connector.setVerified(contract.path("verified").asBoolean());
    JsonNode lastVerifiedDateNode = contract.path("last_verified_date");

    if (lastVerifiedDateNode != null && !lastVerifiedDateNode.isNull()) {
      String lastVerifiedDate = lastVerifiedDateNode.asText();
      if (lastVerifiedDate != null
          && !lastVerifiedDate.isBlank()
          && !"null".equals(lastVerifiedDate)) {
        connector.setLastVerifiedDate(TimeUtils.toInstantFlexible(lastVerifiedDate));
      }
    }
    connector.setUseCases(new HashSet<>(useCases));
    connector.setPlaybookSupported(contract.path("playbook_supported").asBoolean());
    connector.setMaxConfidenceLevel(contract.path("max_confidence_level").asInt());
    connector.setSupportVersion(contract.path("support_version").asText());
    connector.setSubscriptionLink(contract.path("subscription_link").asText());
    connector.setSourceCode(contract.path("source_code").asText());
    connector.setManagerSupported(contract.path("manager_supported").asBoolean());
    connector.setContainerVersion(contract.path("container_version").asText());
    connector.setContainerImage(contract.path("container_image").asText());
    String containerType = contract.path("container_type").asText(null);
    if (containerType != null && !containerType.isBlank()) {
      try {
        connector.setContainerType(
            CatalogConnector.CONNECTOR_TYPE.valueOf(containerType.trim().toUpperCase()));
      } catch (IllegalArgumentException e) {
        log.warn("Unknown container_type '{}', ignoring it", containerType);
      }
    } else {
      log.warn("container_type is null or empty");
    }

    Set<CatalogConnectorConfiguration> conf = buildConnectorConfigurations(contract, connector);
    connector.setCatalogConnectorConfigurations(conf);

    return connector;
  }

  Set<CatalogConnectorConfiguration> buildConnectorConfigurations(
      JsonNode contract, CatalogConnector connector) {
    Set<CatalogConnectorConfiguration> configs = new HashSet<>();

    JsonNode schema = contract.get("config_schema");
    if (schema == null || schema.isNull()) return configs;

    JsonNode properties = schema.get("properties");
    JsonNode required = schema.get("required");

    if (properties == null || properties.isNull()) return configs;

    for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      JsonNode prop = properties.get(key);

      CatalogConnectorConfiguration conf =
          connector.getCatalogConnectorConfigurations().stream()
              .filter(c -> key.equals(c.getConnectorConfigurationKey()))
              .findFirst()
              .orElse(new CatalogConnectorConfiguration());
      conf.setCatalogConnector(connector);
      conf.setConnectorConfigurationKey(key);

      // description
      conf.setConnectorConfigurationDescription(prop.path("description").asText(null));

      // type
      String connectorConfigurationType = prop.path("type").asText(null);
      if (connectorConfigurationType != null && !connectorConfigurationType.isBlank()) {
        try {
          conf.setConnectorConfigurationType(
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.valueOf(
                  connectorConfigurationType.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
          log.warn("Unknown type '{}', ignoring it", connectorConfigurationType);
        }
      } else {
        log.warn("type is null or empty");
      }

      // format
      String connectorConfigurationFormat = prop.path("format").asText(null);
      if (connectorConfigurationFormat != null && !connectorConfigurationFormat.isBlank()) {
        try {
          conf.setConnectorConfigurationFormat(
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.valueOf(
                  connectorConfigurationFormat.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
          log.warn("Unknown format '{}', ignoring it", connectorConfigurationFormat);
        }
      } else {
        log.warn("format is null or empty");
      }

      // default
      JsonNode defaultNode = prop.path("default");
      conf.setConnectorConfigurationDefault(
          defaultNode != null && !defaultNode.isNull() ? defaultNode : null);

      // enum
      if (prop.has("enum") && prop.path("enum").isArray()) {
        List<String> enums = new ArrayList<>();
        for (JsonNode e : prop.path("enum")) enums.add(e.asText());
        conf.setConnectorConfigurationEnum(new HashSet<>(enums));
      }

      // required
      boolean isRequired = false;
      if (required != null && required.isArray()) {
        for (JsonNode req : required) {
          if (req.asText().equals(key)) {
            isRequired = true;
            break;
          }
        }
      }
      conf.setConnectorConfigurationRequired(isRequired);

      // writeOnly
      conf.setConnectorConfigurationWriteOnly(prop.path("writeOnly").asBoolean(false));

      configs.add(conf);
    }

    return configs;
  }

  private String uploadBase64Image(String base64Image, String connectorSlug) {
    try {
      String base64Data = base64Image;

      if (base64Image.startsWith("data:")) {
        String[] parts = base64Image.split(",");
        if (parts.length == 2) {
          base64Data = parts[1];
        }
      }

      byte[] imageBytes = Base64.getDecoder().decode(base64Data);
      InputStream dataStream = new ByteArrayInputStream(imageBytes);

      String fileName = connectorSlug + "-logo.png";

      fileService.uploadStream(FileService.CONNECTORS_LOGO_PATH, fileName, dataStream);

      return fileName;
    } catch (Exception e) {
      log.error("Error upload image MinIO", e);
      return "img/icon-connector-default.png";
    }
  }
}
