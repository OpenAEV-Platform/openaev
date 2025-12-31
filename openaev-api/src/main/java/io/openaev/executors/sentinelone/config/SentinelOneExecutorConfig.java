package io.openaev.executors.sentinelone.config;

import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_DEFAULT_ID;

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
@ConfigurationProperties(prefix = "executor.sentinelone")
public class SentinelOneExecutorConfig extends BaseIntegrationConfiguration {
  private static final String API_URI = "/web/api/v2.1/";

  @IntegrationConfigKey(
      key = "EXECUTOR_ID",
      description =
          """
          ID of the builtin SentinelOne executor
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String id = SENTINELONE_EXECUTOR_DEFAULT_ID;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_URL",
      description =
          """
          SentinelOne URL, the API version used is the 2.1
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String url;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_API_KEY",
      description =
          """
          SentinelOne API key
          """,
      valueFormat = CONNECTOR_CONFIGURATION_FORMAT.PASSWORD,
      isRequired = true)
  @Getter
  @NotBlank
  private String apiKey;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_API_BATCH_EXECUTION_ACTION_PAGINATION",
      description =
          """
          SentinelOne API pagination per 5 seconds to set for agents batch executions (number of agents sent per 5 seconds to SentinelOne to execute a payload)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer apiBatchExecutionActionPagination = 2500;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_API_REGISTER_INTERVAL",
      description =
          """
          SentinelOne API interval to register/update the accounts/sites/groups/agents in OpenAEV (in seconds)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer apiRegisterInterval = 1200;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_CLEAN_IMPLANT_INTERVAL",
      description =
          """
          SentinelOne clean old implant interval (in hours)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer cleanImplantInterval = 8;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_ACCOUNT_ID",
      description =
          """
          SentinelOne account id or accounts ids separated with commas (optional if site or group is filled)
          """)
  @Getter
  @NotBlank
  private String accountId;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_SITE_ID",
      description =
          """
          SentinelOne site id or sites ids separated with commas (optional if account or group is filled)
          """)
  @Getter
  @NotBlank
  private String siteId;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_GROUP_ID",
      description =
          """
          SentinelOne group id or groups ids separated with commas (optional if site or account is filled)
          """)
  @Getter
  @NotBlank
  private String groupId;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_WINDOWS_SCRIPT_ID",
      description =
          """
          Id of the OpenAEV SentinelOne Windows script
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String windowsScriptId;

  @IntegrationConfigKey(
      key = "EXECUTOR_SENTINELONE_UNIX_SCRIPT_ID",
      description =
          """
          Id of the OpenAEV SentinelOne Unix script
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String unixScriptId;

  public String getApiUrl() {
    return url + API_URI;
  }
}
