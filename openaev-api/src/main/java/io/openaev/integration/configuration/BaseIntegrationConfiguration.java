package io.openaev.integration.configuration;

import static io.openaev.database.model.CatalogConnectorConfiguration.ENCRYPTED_FORMATS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BaseIntegrationConfiguration {
  private final ObjectMapper mapper = new ObjectMapper();

  public Set<ConnectorInstanceConfiguration> toInstanceConfigurationSet(
      ConnectorInstance relatedInstance) {
    List<Field> annotatedFields =
        FieldUtils.getAllDeclaredAnnotatedFields(this.getClass(), IntegrationConfigKey.class);
    return annotatedFields.stream()
        .map(
            af ->
                ConnectorInstanceConfiguration.builder()
                    .key(af.getAnnotation(IntegrationConfigKey.class).key())
                    .value(mapper.valueToTree(FieldUtils.getField(this, af)))
                    .isEncrypted(
                        ENCRYPTED_FORMATS.contains(
                            af.getAnnotation(IntegrationConfigKey.class).valueFormat()))
                    .connectorInstance(relatedInstance)
                    .build())
        .collect(Collectors.toSet());
  }

  public Set<CatalogConnectorConfiguration> toCatalogConfigurationSet(
      CatalogConnector relatedCatalogConnector) {
    List<Field> annotatedFields =
        FieldUtils.getAllDeclaredAnnotatedFields(this.getClass(), IntegrationConfigKey.class);
    return annotatedFields.stream()
        .map(
            af ->
                CatalogConnectorConfiguration.builder()
                    .connectorConfigurationRequired(
                        af.getAnnotation(IntegrationConfigKey.class).isRequired())
                    .connectorConfigurationWriteOnly(
                        ENCRYPTED_FORMATS.contains(
                            af.getAnnotation(IntegrationConfigKey.class).valueFormat()))
                    .connectorConfigurationDefault(
                        mapper.valueToTree(FieldUtils.getField(this, af)))
                    .connectorConfigurationKey(af.getAnnotation(IntegrationConfigKey.class).key())
                    .connectorConfigurationType(
                        af.getAnnotation(IntegrationConfigKey.class).jsonType())
                    .connectorConfigurationFormat(
                        af.getAnnotation(IntegrationConfigKey.class).valueFormat())
                    .connectorConfigurationDescription(
                        af.getAnnotation(IntegrationConfigKey.class).description())
                    .connectorConfigurationEnum(
                        af.getAnnotation(IntegrationConfigKey.class).refEnumClass()
                                != IntegrationConfigKey.Unassigned.class
                            ? Arrays.stream(
                                    af.getAnnotation(IntegrationConfigKey.class)
                                        .refEnumClass()
                                        .getEnumConstants())
                                .map(Enum::toString)
                                .collect(Collectors.toSet())
                            : null)
                    .catalogConnector(relatedCatalogConnector)
                    .build())
        .collect(Collectors.toSet());
  }
}
