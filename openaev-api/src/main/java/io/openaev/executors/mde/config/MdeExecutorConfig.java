package io.openaev.executors.mde.config;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_DEFAULT_ID;
import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;

import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT;
import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "executor.mde")
public class MdeExecutorConfig extends BaseIntegrationConfiguration {

  /** Defaults reused by both the field initializers and the null-guards on the consuming paths. */
  public static final int DEFAULT_API_BATCH_EXECUTION_ACTION_PAGINATION = 10;

  public static final int DEFAULT_API_REGISTER_INTERVAL = 1200;
  public static final int DEFAULT_STALE_PENDING_THRESHOLD_MINUTES = 30;

  @IntegrationConfigKey(
      key = "EXECUTOR_ID",
      description = "ID of the builtin Microsoft Defender for Endpoint executor",
      isRequired = true)
  @Getter
  @NotBlank
  private String id = MDE_EXECUTOR_DEFAULT_ID;

  @IntegrationConfigKey(
      key = "EXECUTOR_NAME",
      description = "Name of the builtin Microsoft Defender for Endpoint executor",
      isRequired = true)
  @Getter
  @NotBlank
  private String name = MDE_EXECUTOR_NAME;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_API_URL",
      description = "Microsoft Defender for Endpoint API base URL")
  @Getter
  @NotBlank
  private String apiUrl = "https://api.securitycenter.microsoft.com/api";

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_AUTH_URL",
      description = "Microsoft Azure Active Directory OAuth2 token endpoint base URL")
  @Getter
  @NotBlank
  private String authUrl = "https://login.microsoftonline.com";

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_AZURE_TENANT_ID",
      isRequired = true,
      description = "Azure Active Directory tenant ID (Directory ID)")
  @Getter
  @NotBlank
  private String azureTenantId;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_CLIENT_ID",
      isRequired = true,
      description = "Azure App Registration client ID with Machine.LiveResponse.All permission")
  @Getter
  @NotBlank
  private String clientId;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_CLIENT_SECRET",
      isRequired = true,
      valueFormat = CONNECTOR_CONFIGURATION_FORMAT.PASSWORD,
      description = "Azure App Registration client secret")
  @Getter
  @NotBlank
  private String clientSecret;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_DEVICE_GROUP",
      isRequired = false,
      description =
          "MDE device group ID(s) separated by commas (rbacGroupId). Leave empty to sync all devices.")
  @Getter
  private String deviceGroup;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_WINDOWS_SCRIPT_NAME",
      isRequired = true,
      description =
          "Name of the OpenAEV Windows subprocessor script uploaded to MDE Live Response Library")
  @Getter
  @NotBlank
  private String windowsScriptName = "openaev-subprocessor.ps1";

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_UNIX_SCRIPT_NAME",
      isRequired = true,
      description =
          "Name of the OpenAEV Unix subprocessor script uploaded to MDE Live Response Library")
  @Getter
  @NotBlank
  private String unixScriptName = "openaev-subprocessor.sh";

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_API_BATCH_EXECUTION_ACTION_PAGINATION",
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          "Number of machines sent per 5 seconds to MDE Live Response API to execute a payload (MDE rate limit is stricter than CrowdStrike)")
  @Getter
  @NotNull
  @Min(1)
  private Integer apiBatchExecutionActionPagination = DEFAULT_API_BATCH_EXECUTION_ACTION_PAGINATION;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_API_REGISTER_INTERVAL",
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          "MDE API interval to register/update device groups and agents in OpenAEV (in seconds)")
  @Getter
  @NotNull
  @Min(1)
  private Integer apiRegisterInterval = DEFAULT_API_REGISTER_INTERVAL;

  @IntegrationConfigKey(
      key = "EXECUTOR_MDE_STALE_PENDING_THRESHOLD_MINUTES",
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          "Age in minutes above which a stuck Pending MDE Live Response action is cancelled before a new dispatch. MDE allows a single Live Response session per machine, so a Pending action left by an offline machine blocks every later inject on it. Kept well above the inject timeout so a concurrent inject's fresh session is never cancelled.")
  @Getter
  @NotNull
  @Min(1)
  private Integer stalePendingThresholdMinutes = DEFAULT_STALE_PENDING_THRESHOLD_MINUTES;
}
