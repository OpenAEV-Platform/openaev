package io.openaev.injectors.ovh.config;

import static io.openaev.integration.impl.injectors.ovh.OvhInjectorIntegration.OVH_SMS_INJECTOR_ID;
import static io.openaev.integration.impl.injectors.ovh.OvhInjectorIntegration.OVH_SMS_INJECTOR_NAME;

import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ovh.sms")
public class OvhSmsInjectorConfig extends BaseIntegrationConfiguration {
  @IntegrationConfigKey(
      key = "INJECTOR_ID",
      description =
          """
                    ID of the builtin Ovh injector
                    """,
      isRequired = true)
  @NotBlank
  private String id = OVH_SMS_INJECTOR_ID;

  @IntegrationConfigKey(
      key = "INJECTOR_NAME",
      description =
          """
                            Name of the builtin Ovh injector
                            """,
      isRequired = true)
  @NotBlank
  private String name = OVH_SMS_INJECTOR_NAME;

  @IntegrationConfigKey(key = "OVH_SMS_AK", description = "OVHCloud Access Key", isRequired = true)
  @NotBlank
  private String ak;

  @IntegrationConfigKey(
      key = "OVH_SMS_AS",
      description = "OVHCloud Access Secret",
      isRequired = true,
      valueFormat = CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD)
  @NotBlank
  private String as;

  @IntegrationConfigKey(
      key = "OVH_SMS_CK",
      description = "OVHCloud consumer key",
      isRequired = true)
  @NotBlank
  private String ck;

  @IntegrationConfigKey(
      key = "OVH_SMS_SERVICE",
      description = "OVHCloud Service Identifier",
      isRequired = true)
  @NotBlank
  private String service;

  @IntegrationConfigKey(
      key = "OVH_SMS_SENDER",
      description = "OVHCloud sms sender",
      isRequired = true)
  @NotBlank
  private String sender;
}
