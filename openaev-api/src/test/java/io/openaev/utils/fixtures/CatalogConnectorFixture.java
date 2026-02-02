package io.openaev.utils.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorType;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import jakarta.annotation.Nullable;
import java.util.Set;

public class CatalogConnectorFixture {
  public static CatalogConnector createCatalogConnectorWithClassName(String className) {
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle(className);
    connector.setSlug(className);
    connector.setClassName(className);
    return connector;
  }

  public static CatalogConnector createDefaultCatalogConnectorManagedByXtmComposer(
      String connectorName) {
    CatalogConnector catalogConnector = new CatalogConnector();
    catalogConnector.setTitle(connectorName);
    catalogConnector.setSlug(connectorName.toLowerCase().replace(" ", "-"));
    catalogConnector.setVerified(true);
    catalogConnector.setManagerSupported(true);
    catalogConnector.setContainerVersion("0.0.0");
    catalogConnector.setContainerType(ConnectorType.COLLECTOR);
    catalogConnector.setContainerImage("openaev/" + connectorName.toLowerCase().replace(" ", "-"));
    return catalogConnector;
  }

  public static CatalogConnector createDefaultCatalogConnectorManagedByXtmComposer(
      String connectorName, ConnectorType connectorType) {
    CatalogConnector catalogConnector =
        createDefaultCatalogConnectorManagedByXtmComposer(connectorName);
    catalogConnector.setContainerType(connectorType);
    return catalogConnector;
  }

  public static CatalogConnectorConfiguration createCatalogConfiguration(
      String key,
      CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE type,
      Boolean required,
      @Nullable String defaultValue,
      @Nullable Set<String> confEnum,
      @Nullable CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT format)
      throws JsonProcessingException {
    CatalogConnectorConfiguration configuration = new CatalogConnectorConfiguration();
    configuration.setConnectorConfigurationKey(key);
    configuration.setConnectorConfigurationType(type);
    configuration.setConnectorConfigurationRequired(required);
    if (defaultValue != null) {
      ObjectMapper mapper = new ObjectMapper();
      String jsonValue = mapper.writeValueAsString(defaultValue);
      configuration.setConnectorConfigurationDefault(mapper.readTree(jsonValue));
    }
    if (confEnum != null) {
      configuration.setConnectorConfigurationEnum(confEnum);
    }
    if (format != null) {
      configuration.setConnectorConfigurationFormat(format);
    }
    return configuration;
  }

  public static CreateConnectorInstanceInput.ConfigurationInput createConfigurationInput(
      String key, String value) throws JsonProcessingException {
    CreateConnectorInstanceInput.ConfigurationInput configurationInput =
        new CreateConnectorInstanceInput.ConfigurationInput();
    configurationInput.setKey(key);
    ObjectMapper mapper = new ObjectMapper();
    String jsonValue = mapper.writeValueAsString(value);
    configurationInput.setValue(mapper.readTree(jsonValue));
    return configurationInput;
  }
}
