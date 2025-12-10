package io.openaev.executors.crowdstrike.config;

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
  @Getter private boolean enable;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_ID")
  @Getter @NotBlank private String id;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_API_URL", isRequired = true)
  @Getter @NotBlank private String apiUrl;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_API_BATCH_EXECUTION_ACTION_PAGINATION", jsonType = "number")
  @Getter @NotBlank private Integer apiBatchExecutionActionPagination = 2500;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_API_REGISTER_INTERVAL", jsonType = "number")
  @Getter @NotBlank private Integer apiRegisterInterval = 1200;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_CLEAN_IMPLANT_INTERVAL", jsonType = "number")
  @Getter @NotBlank private Integer cleanImplantInterval = 8;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_CLIENT_ID", isRequired = true)
  @Getter @NotBlank private String clientId;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_CLIENT_SECRET", isEncrypted = true, isRequired = true)
  @Getter @NotBlank private String clientSecret;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_HOST_GROUP", isRequired = true)
  @Getter @NotBlank private String hostGroup;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_WINDOWS_SCRIPT_NAME", isRequired = true)
  @Getter @NotBlank private String windowsScriptName;

  @IntegrationConfigKey(key = "EXECUTOR_CROWDSTRIKE_UNIX_SCRIPT_NAME", isRequired = true)
  @Getter @NotBlank private String unixScriptName;
}
