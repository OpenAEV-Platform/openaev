package io.openaev.executors.crowdstrike.config;

import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_DEFAULT_ID;

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
@ConfigurationProperties(prefix = "executor.crowdstrike")
public class CrowdStrikeExecutorConfig extends BaseIntegrationConfiguration {
  @IntegrationConfigKey(
      key = "EXECUTOR_ID",
      description =
          """
          ID of the builtin Crowdstrike executor
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String id = CROWDSTRIKE_EXECUTOR_DEFAULT_ID;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_API_URL", description = "Crowdstrike API url")
  @Getter
  @NotBlank
  private String apiUrl = "https://api.us-2.crowdstrike.com";

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_API_BATCH_EXECUTION_ACTION_PAGINATION",
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          """
          Crowdstrike API pagination per 5 seconds to set for hosts batch executions (number of hosts sent per 5 seconds to Crowdstrike to execute a payload)
          """)
  @Getter
  @NotBlank
  private Integer apiBatchExecutionActionPagination = 2500;

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_API_REGISTER_INTERVAL",
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          """
          Crowdstrike API interval to register/update the host groups/hosts/agents in OpenAEV (in seconds)
          """)
  @Getter
  @NotBlank
  private Integer apiRegisterInterval = 1200;

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_CLEAN_IMPLANT_INTERVAL",
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          """
          Crowdstrike clean old implant interval (in hours)
          """)
  @Getter
  @NotBlank
  private Integer cleanImplantInterval = 8;

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_CLIENT_ID",
      isRequired = true,
      description = "Crowdstrike client id")
  @Getter
  @NotBlank
  private String clientId;

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_CLIENT_SECRET",
      isRequired = true,
      valueFormat = CONNECTOR_CONFIGURATION_FORMAT.PASSWORD,
      description = "Crowdstrike client secret")
  @Getter
  @NotBlank
  private String clientSecret;

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_HOST_GROUP",
      isRequired = true,
      description = "Crowdstrike host group id or hosts groups ids separated with commas")
  @Getter
  @NotBlank
  private String hostGroup;

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_WINDOWS_SCRIPT_NAME",
      isRequired = true,
      description = "Name of the OpenAEV Crowdstrike windows script")
  @Getter
  @NotBlank
  private String windowsScriptName = "OpenAEV Subprocessor (Windows)";

  @IntegrationConfigKey(
      key = "EXECUTOR_CROWDSTRIKE_UNIX_SCRIPT_NAME",
      isRequired = true,
      description = "Name of the OpenAEV Crowdstrike unix script")
  @Getter
  @NotBlank
  private String unixScriptName = "OpenAEV Subprocessor (Unix)";
}
