 package io.openaev.runner;

 import com.fasterxml.jackson.databind.JsonNode;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import io.openaev.database.model.CatalogConnectorConfiguration;
 import io.openaev.service.CatalogConnectorConfigurationService;
 import io.openaev.service.CatalogConnectorService;
 import io.openaev.database.model.CatalogConnector;

 import java.io.ByteArrayInputStream;
 import java.io.IOException;
 import java.io.InputStream;
 import java.util.*;

 import io.openaev.service.FileService;
 import io.openaev.utils.TimeUtils;
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
  private final CatalogConnectorConfigurationService catalogConnectorConfigurationService;
  private final FileService fileService;

  @Override
  public void run(String... args) {
    String resourcePath = "/catalog/catalog-integrators.json";

    try (InputStream is = CatalogConnectorIngestionRunner.class.getResourceAsStream(resourcePath))
 {
      if (is == null) {
        throw new IOException("Fichier non trouvé : " + resourcePath);
      }

      JsonNode rootNode = mapper.readTree(is);

      List<CatalogConnector> catalog = extractCatalog(rootNode);

      System.out.println("Catalogue construit :");
      for (CatalogConnector connector : catalog) {
        System.out.println("  • " + connector.getTitle());
      }
    } catch (IOException e) {
      System.err.println("Erreur de lecture du fichier : " + e.getMessage());
    }
  }

  private List<CatalogConnector> extractCatalog(JsonNode rootNode) {
    List<CatalogConnector> catalogConnectors = new ArrayList<>();

    JsonNode contracts = rootNode.get("contracts");
    if (contracts != null && contracts.isArray()) {
      for (JsonNode contract : contracts) {
          Optional<CatalogConnector> catalog = buildCatalogConnector(contract);
          catalog.ifPresent(catalogConnectors::add);
      }
    }
    List<CatalogConnector> savedConnectors = catalogConnectorService.upsertAll(catalogConnectors);

    if (contracts != null && contracts.isArray()) {
      int index = 0;
      for (JsonNode contract : contracts) {
        CatalogConnector savedConnector = savedConnectors.get(index);

        List<CatalogConnectorConfiguration> configs = buildConnectorConfigurations(contract,
 savedConnector);
        catalogConnectorConfigurationService.upsertAll(configs);

        index++;
      }
    }

    return savedConnectors;
  }

  private Optional<CatalogConnector> buildCatalogConnector(JsonNode contract) {
    CatalogConnector connector = new CatalogConnector();

    List<String> useCases = new ArrayList<>();
    JsonNode arrUseCases = contract.get("use_cases");
    if (arrUseCases != null && arrUseCases.isArray()) {
      for (JsonNode uc : arrUseCases) {
        useCases.add(uc.asText());
      }
    }
    connector.setUseCases(new HashSet<>(useCases));

    connector.setTitle(contract.get("title").asText());
    connector.setSlug(contract.get("slug").asText());
    connector.setDescription(contract.get("description").asText());
    connector.setShortDescription(contract.get("short_description").asText());
    String base64Logo = contract.path("logo").asText(null);
    if (base64Logo != null && !base64Logo.isBlank()) {
      String logoPath = uploadBase64Image(base64Logo, contract.path("slug").asText());
      connector.setLogoUrl(logoPath);
    }

    connector.setVerified(contract.get("verified").asBoolean());
    JsonNode lastVerifiedDateNode = contract.get("last_verified_date");

    if (lastVerifiedDateNode != null && !lastVerifiedDateNode.isNull()) {
      String lastVerifiedDate = lastVerifiedDateNode.asText();
      if (lastVerifiedDate != null && !lastVerifiedDate.isBlank() &&
 !"null".equals(lastVerifiedDate)) {
        connector.setLastVerifiedDate(TimeUtils.toInstantFlexible(lastVerifiedDate));
      }
    }
    connector.setUseCases(new HashSet<>(useCases));
    connector.setPlaybookSupported(contract.get("playbook_supported").asBoolean());
    connector.setMaxConfidenceLevel(contract.get("max_confidence_level").asInt());
    connector.setSupportVersion(contract.get("support_version").asText());
    connector.setSubscriptionLink(contract.get("subscription_link").asText());
    connector.setSourceCode(contract.get("source_code").asText());
    connector.setManagerSupported(contract.get("manager_supported").asBoolean());
    connector.setContainerVersion(contract.get("container_version").asText());
    connector.setContainerImage(contract.get("container_image").asText());
    String containerType = contract.path("container_type").asText(null);
    if (containerType != null) {
        connector.setContainerType(CatalogConnector.CONNECTOR_TYPE.valueOf(containerType.trim().toUpperCase()));
    } else{
        log.error("container_type is null");
        return Optional.empty();
    }
    return Optional.of(connector);
  }

  private List<CatalogConnectorConfiguration> buildConnectorConfigurations(JsonNode contract,
 CatalogConnector connector) {
    List<CatalogConnectorConfiguration> configs = new ArrayList<>();

    JsonNode schema = contract.get("config_schema");
    if (schema == null || schema.isNull()) return configs;

    JsonNode properties = schema.get("properties");
    JsonNode required = schema.get("required");

    if (properties == null || properties.isNull()) return configs;

    for (Iterator<String> it = properties.fieldNames(); it.hasNext();) {
      String key = it.next();
      JsonNode prop = properties.get(key);

      CatalogConnectorConfiguration conf = new CatalogConnectorConfiguration();
      conf.setCatalogConnector(connector);
      conf.setConnectorConfigurationKey(key);

      // description
      conf.setConnectorConfigurationDescription(prop.path("description").asText(null));

      // type & format
      conf.setConnectorConfigurationType(prop.path("type").asText(null));
      conf.setConnectorConfigurationFormat(prop.path("format").asText(null));

      // default
      JsonNode defaultNode = prop.get("default");
      conf.setConnectorConfigurationDefault(defaultNode != null && !defaultNode.isNull() ?
 defaultNode : null);

      // enum
      if (prop.has("enum") && prop.get("enum").isArray()) {
        List<String> enums = new ArrayList<>();
        for (JsonNode e : prop.get("enum")) enums.add(e.asText());
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

      return fileService.uploadStream(FileService.CONNECTORS_LOGO_PATH, fileName, dataStream);

    } catch (Exception e) {
      log.error("Error upload image MinIO", e);
      return null;
    }
  }

 }
