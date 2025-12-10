package io.openaev.integration.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.utils.reflection.FieldUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BaseIntegrationConfiguration {
  private final ObjectMapper mapper = new ObjectMapper();
  public Set<ConnectorInstanceConfiguration> toInstanceConfigurationSet(ConnectorInstance relatedInstance) {
    List<Field> annotatedFields = FieldUtils.getAllDeclaredAnnotatedFields(this.getClass(), IntegrationConfigKey.class);
    return annotatedFields.stream().map(af ->
      ConnectorInstanceConfiguration.builder()
              .key(af.getAnnotation(IntegrationConfigKey.class).key())
              .value(mapper.valueToTree(FieldUtils.getField(this, af)))
              .isEncrypted(af.getAnnotation(IntegrationConfigKey.class).isEncrypted())
              .connectorInstance(relatedInstance)
              .build()
    ).collect(Collectors.toSet());
  }

  public Set<CatalogConnectorConfiguration> toCatalogConfigurationSet(CatalogConnector relatedCatalogConnector) {
    List<Field> annotatedFields = FieldUtils.getAllDeclaredAnnotatedFields(this.getClass(), IntegrationConfigKey.class);
    return annotatedFields.stream().map(af ->
            CatalogConnectorConfiguration.builder()
                    .connectorConfigurationRequired(af.getAnnotation(IntegrationConfigKey.class).isRequired())
                    .connectorConfigurationWriteOnly(af.getAnnotation(IntegrationConfigKey.class).isEncrypted())
                    .connectorConfigurationDefault(mapper.valueToTree(FieldUtils.getField(this, af)))
                    .connectorConfigurationKey(af.getAnnotation(IntegrationConfigKey.class).key())
                    .connectorConfigurationType()
                    .catalogConnector(relatedCatalogConnector)
                    .key(af.getAnnotation(IntegrationConfigKey.class).key())
                    .value(mapper.valueToTree(FieldUtils.getField(this, af)))
                    .isEncrypted(af.getAnnotation(IntegrationConfigKey.class).isEncrypted())
                    .connectorInstance(relatedInstance)
                    .build()
    ).collect(Collectors.toSet());
  }
}
