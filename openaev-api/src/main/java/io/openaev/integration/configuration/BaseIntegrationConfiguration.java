package io.openaev.integration.configuration;

import static io.openaev.database.model.CatalogConnectorConfiguration.ENCRYPTED_FORMATS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.rest.exception.UnencryptableElementException;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.service.connector_instances.EncryptionService;
import io.openaev.utils.JsonUtils;
import io.openaev.utils.reflection.FieldUtils;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseIntegrationConfiguration {
  private final ObjectMapper mapper = new ObjectMapper();
  @Getter @Setter private boolean enable = false;

  @Setter private EncryptionFactory encryptionFactory;

  public <T extends BaseIntegrationConfiguration> void fromConnectorInstanceConfigurationSet(
      @NotNull ConnectorInstance instance, Class<T> targetClass) throws JsonProcessingException {
    EncryptionService encryptionService = null;
    if (instance instanceof ConnectorInstancePersisted) {
      encryptionService =
          encryptionFactory.getEncryptionService(
              ((ConnectorInstancePersisted) instance).getCatalogConnector());
    } else {
      log.warn("Cannot instantiate encryption service: connectorInstance is not persisted");
    }
    List<Field> annotatedFields =
        FieldUtils.getAllDeclaredAnnotatedFields(targetClass, IntegrationConfigKey.class);
    for (Field field : annotatedFields) {
      Optional<ConnectorInstanceConfiguration> config =
          instance.getConfigurations().stream()
              .filter(c -> c.getKey().equals(field.getAnnotation(IntegrationConfigKey.class).key()))
              .findFirst();
      Object value = null;
      if (config.isPresent() && config.get().isEncrypted() && encryptionService != null) {
        // If the field is encrypted and can be decrypted
        // Decrypt the field
        value =
            JsonUtils.fromJsonNode(
                new ObjectMapper()
                    .valueToTree(encryptionService.decrypt(config.get().getValue().asText())),
                field.getType());
      } else if (config.isPresent()) {
        // Otherwise, we just get the value from the JSON node
        value = JsonUtils.fromJsonNode(config.get().getValue(), field.getType());
      }
      FieldUtils.setField(this, field, value);
    }
  }

  public Set<ConnectorInstanceConfiguration> toInstanceConfigurationSet(
      ConnectorInstancePersisted relatedInstance, EncryptionService encryptionService) {
    List<Field> annotatedFields =
        FieldUtils.getAllDeclaredAnnotatedFields(this.getClass(), IntegrationConfigKey.class);
    return annotatedFields.stream()
        .map(
            af -> {
              JsonNode value = mapper.valueToTree(FieldUtils.getField(this, af));
              boolean isEncrypted =
                  ENCRYPTED_FORMATS.contains(
                      af.getAnnotation(IntegrationConfigKey.class).valueFormat());
              // If the field is encrypted
              if (isEncrypted && encryptionService != null) {
                // If the encryption service is not null, we use it
                try {
                  value = mapper.valueToTree(encryptionService.encrypt(value.asText()));
                } catch (Exception e) {
                  throw new UnencryptableElementException(
                      "Cannot encrypt the element : " + af.getName(), e);
                }
              } else if (isEncrypted) {
                // If the encryption service is null, there might be an issue with how the
                // executor has been initialized
                log.warn(
                    "A encrypted element cannot be decrypted due to the encryption service being null. You might want to look into that as this can cause issue.");
              }

              return ConnectorInstanceConfiguration.builder()
                  .key(af.getAnnotation(IntegrationConfigKey.class).key())
                  .value(value)
                  .isEncrypted(isEncrypted)
                  .connectorInstance(relatedInstance)
                  .build();
            })
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
