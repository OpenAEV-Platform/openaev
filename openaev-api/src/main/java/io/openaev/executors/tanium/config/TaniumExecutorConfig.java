package io.openaev.executors.tanium.config;

import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_DEFAULT_ID;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;

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
@ConfigurationProperties(prefix = "executor.tanium")
public class TaniumExecutorConfig extends BaseIntegrationConfiguration {
  private static final String GATEWAY_URI = "/plugin/products/gateway/graphql";

  @IntegrationConfigKey(
      key = "EXECUTOR_ID",
      description =
          """
          ID of the builtin Tanium executor
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String id = TANIUM_EXECUTOR_DEFAULT_ID;

  @IntegrationConfigKey(
      key = "EXECUTOR_NAME",
      description =
          """
                  Name of the builtin Caldera executor
                  """,
      isRequired = true)
  @Getter
  @NotBlank
  private String name = TANIUM_EXECUTOR_NAME;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_API_URL",
      description =
          """
          Tanium API URL
          """)
  @Getter
  @NotBlank
  private String url;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_API_BATCH_EXECUTION_ACTION_PAGINATION",
      description =
          """
          Tanium API pagination per 5 seconds to set for endpoints batch executions (number of endpoints sent per 5 seconds to Tanium to execute a payload)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer apiBatchExecutionActionPagination = 100;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_API_REGISTER_INTERVAL",
      description =
          """
          Tanium API interval to register/update the computer groups/endpoints in OpenAEV (in seconds)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer apiRegisterInterval = 1200;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_CLEAN_IMPLANT_INTERVAL",
      description =
          """
          Tanium clean old implant interval (in hours)
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer cleanImplantInterval = 8;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_API_KEY",
      description =
          """
          Tanium API key
          """,
      valueFormat = CONNECTOR_CONFIGURATION_FORMAT.PASSWORD)
  @Getter
  @NotBlank
  private String apiKey;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_COMPUTER_GROUP_ID",
      description =
          """
          Tanium Computer Group to be used in simulations
          """)
  @Getter
  @NotBlank
  private String computerGroupId = "1";

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_ACTION_GROUP_ID",
      description =
          """
          Tanium Action Group to apply actions to
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer actionGroupId = 4;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_WINDOWS_PACKAGE_ID",
      description =
          """
          ID of the OpenAEV Tanium Windows package
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer windowsPackageId;

  @IntegrationConfigKey(
      key = "EXECUTOR_TANIUM_UNIX_PACKAGE_ID",
      description =
          """
          ID of the OpenAEV Tanium Unix package
          """,
      jsonType = CONNECTOR_CONFIGURATION_TYPE.INTEGER)
  @Getter
  @NotBlank
  private Integer unixPackageId;

  public String getGatewayUrl() {
    return url + GATEWAY_URI;
  }
}
