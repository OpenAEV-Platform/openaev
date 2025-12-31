package io.openaev.executors.caldera.config;

import static io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegration.CALDERA_EXECUTOR_DEFAULT_ID;

import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "executor.caldera")
public class CalderaExecutorConfig extends BaseIntegrationConfiguration {
  private static final String REST_V2_URI = "/api/v2";
  private static final String PLUGIN_ACCESS_URI = "/plugin/access";

  @IntegrationConfigKey(
      key = "EXECUTOR_ID",
      description =
          """
          ID of the builtin Caldera executor
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String id = CALDERA_EXECUTOR_DEFAULT_ID;

  @IntegrationConfigKey(
      key = "EXECUTOR_CALDERA_URL",
      description =
          """
          Caldera URL
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String url;

  @IntegrationConfigKey(
      key = "EXECUTOR_CALDERA_PUBLIC_URL",
      description =
          """
          Caldera URL accessible from endpoints (ex: http://caldera.myopenaev.myorganization.com:8888)
          """,
      isRequired = true)
  @Getter
  @NotBlank
  private String publicUrl;

  @IntegrationConfigKey(
      key = "EXECUTOR_CALDERA_API_KEY",
      description =
          """
          Caldera API key
          """,
      isRequired = true,
      valueFormat = CONNECTOR_CONFIGURATION_FORMAT.PASSWORD)
  @Getter
  @NotBlank
  private String apiKey;

  public String getRestApiV2Url() {
    return url + REST_V2_URI;
  }

  public String getPluginAccessApiUrl() {
    return url + PLUGIN_ACCESS_URI;
  }
}
