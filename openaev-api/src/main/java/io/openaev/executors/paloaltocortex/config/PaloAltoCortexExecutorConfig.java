package io.openaev.executors.paloaltocortex.config;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_DEFAULT_ID;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT;
import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "executor.paloaltocortex")
public class PaloAltoCortexExecutorConfig extends BaseIntegrationConfiguration {
  private static final String API_URI = "/public_api/v1/";

  @IntegrationConfigKey(
      key = "EXECUTOR_ID",
      description =
          """
          ID of the builtin Palo Alto Cortex executor
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String id = PALOALTOCORTEX_EXECUTOR_DEFAULT_ID;

  @IntegrationConfigKey(
      key = "EXECUTOR_NAME",
      description =
          """
                          Name of the builtin Palo Alto Cortex executor
                          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String name = PALOALTOCORTEX_EXECUTOR_NAME;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_URL",
      description =
          """
          Palo Alto Cortex URL, the API version used is the 1.0
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String url;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_API_KEY_ID",
      description =
          """
                  Palo Alto Cortex API key id
                  """,
      isRequired = true)
  @Getter
  @NotBlank
  private String apiKeyId;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_API_KEY",
      description =
          """
          Palo Alto Cortex API key
          """,
      valueFormat = CONNECTOR_CONFIGURATION_FORMAT.PASSWORD,
      isRequired = true)
  @Getter
  @NotBlank
  private String apiKey;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_API_BATCH_EXECUTION_ACTION_PAGINATION",
      description =
          """
          Palo Alto Cortex API pagination per 5 seconds to set for endpoints batch executions (number of endpoints sent per 5 seconds to Palo Alto Cortex to execute a payload)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer apiBatchExecutionActionPagination = 100;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_API_REGISTER_INTERVAL",
      description =
          """
          Palo Alto Cortex API interval to register/update the groups/endpoints in OpenAEV (in seconds)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer apiRegisterInterval = 1200;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_CLEAN_IMPLANT_INTERVAL",
      description =
          """
          Palo Alto Cortex clean old implant interval (in hours)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer cleanImplantInterval = 8;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_GROUP_NAME",
      description =
          """
          Palo Alto Cortex group name or groups names separated with commas
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String groupName;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_WINDOWS_SCRIPT_UID",
      description =
          """
          Uid of the OpenAEV Palo Alto Cortex Windows script
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String windowsScriptUid;

  @IntegrationConfigKey(
      key = "EXECUTOR_PALOALTOCORTEX_UNIX_SCRIPT_UID",
      description =
          """
          Uid of the OpenAEV Palo Alto Cortex Unix script
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String unixScriptUid;

  public String getApiUrl() {
    return url + API_URI;
  }
}
