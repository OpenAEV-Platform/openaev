package io.openaev.service.catalog_connectors;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.service.FileService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.TimeUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CatalogConnectorIngestionService {
  private static final Set<String> PROTECTED_KEYS =
      Set.of("COLLECTOR_ID", "INJECTOR_ID", "EXECUTOR_ID");
  private static final String openAEVKeyName = "OPENAEV_URL";
  private final CatalogConnectorService catalogConnectorService;
  private final FileService fileService;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  public List<CatalogConnector> extractCatalog(JsonNode rootNode) {
    JsonNode contracts = rootNode.get("contracts");
    if (contracts == null) {
      throw new IllegalArgumentException("contracts is null");
    }

    List<CatalogConnector> catalogConnectorList = new ArrayList<>();

    for (JsonNode contract : contracts) {
      CatalogConnector catalogConnector = buildCatalogConnector(contract);
      catalogConnectorList.add(catalogConnector);
    }

    List<CatalogConnector> saved = catalogConnectorService.saveAll(catalogConnectorList);

    for (CatalogConnector connector : saved) {
      cleanupInstanceConfigurations(connector);
    }

    return saved;
  }

  public CatalogConnector buildCatalogConnector(JsonNode contract) {
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
        connector.setContainerType(ConnectorType.valueOf(containerType.trim().toUpperCase()));
      } catch (IllegalArgumentException e) {
        log.warn("Unknown container_type '{}', ignoring it", containerType);
      }
    } else {
      log.warn("container_type is null or empty");
    }

    Set<CatalogConnectorConfiguration> conf = buildConnectorConfigurations(contract, connector);
    connector.getCatalogConnectorConfigurations().clear();
    connector.getCatalogConnectorConfigurations().addAll(conf);

    return connector;
  }

  private void cleanupInstanceConfigurations(CatalogConnector connector) {

    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connector);

    if (instances.isEmpty()) return;

    Map<String, CatalogConnectorConfiguration> schemaByKey =
        connector.getCatalogConnectorConfigurations().stream()
            .collect(
                Collectors.toMap(
                    CatalogConnectorConfiguration::getConnectorConfigurationKey, c -> c));

    List<ConnectorInstanceConfiguration> toDelete = new ArrayList<>();

    for (ConnectorInstancePersisted instance : instances) {

      List<ConnectorInstanceConfiguration> instConfs =
          connectorInstanceConfigurationRepository.findByConnectorInstanceId(instance.getId());

      for (ConnectorInstanceConfiguration instConf : instConfs) {

        // Retrieve the corresponding schema configuration (null if key does not exist anymore)
        CatalogConnectorConfiguration schemaConf = schemaByKey.get(instConf.getKey());

        // Configuration key was removed from the schema:
        // - schemaConf == null means the schema no longer defines this key
        // - Protected system keys must NEVER be deleted
        boolean keyRemovedFromSchema =
            schemaConf == null && !PROTECTED_KEYS.contains(instConf.getKey().toUpperCase());

        // Configuration changed to "PASSWORD" format but the stored value is not encrypted:
        // - schemaConf exists
        // - format is PASSWORD
        // - must be deleted for security
        // TODO : we must encrypt instead of delete
        boolean mustDeleteBecausePassword =
            schemaConf != null && isFormatPasswordButNotEncrypted(schemaConf, instConf);

        if (keyRemovedFromSchema || mustDeleteBecausePassword) {
          toDelete.add(instConf);
        }
      }
    }

    if (!toDelete.isEmpty()) {
      connectorInstanceConfigurationRepository.deleteAll(toDelete);
      log.info(
          "Deleted {} outdated instance configs (schema removed key or PASSWORD switch) for connector {}",
          toDelete.size(),
          connector.getSlug());
    }
  }

  private boolean isFormatPasswordButNotEncrypted(
      CatalogConnectorConfiguration schemaConf, ConnectorInstanceConfiguration instConf) {
    return schemaConf.getConnectorConfigurationKey().equals(instConf.getKey())
        && CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD.equals(
            schemaConf.getConnectorConfigurationFormat())
        && !instConf.isEncrypted();
  }

  public Set<CatalogConnectorConfiguration> buildConnectorConfigurations(
      JsonNode contract, CatalogConnector connector) {
    Set<CatalogConnectorConfiguration> configs = new HashSet<>();

    JsonNode schema = contract.get("config_schema");
    if (schema == null || schema.isNull()) return configs;

    JsonNode properties = schema.get("properties");
    JsonNode required = schema.get("required");

    if (properties == null || properties.isNull()) return configs;

    for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      if (openAEVKeyName.equals(key)) {
        continue;
      }
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
      } else {
        conf.setConnectorConfigurationEnum(null);
      }

      // required
      boolean isRequired = false;
      if (required != null && required.isArray()) {
        for (JsonNode req : required) {
          String reqKey = req.asText();

          // case: required key not in properties -> log
          if (!properties.has(reqKey)) {
            log.warn(
                "Required key '{}' is listed in config_schema.required but missing in properties",
                reqKey);
            continue;
          }

          if (reqKey.equals(key)) {
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
