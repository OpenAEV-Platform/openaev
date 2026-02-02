package io.openaev.injectors.opencti.config;

import static io.openaev.integration.impl.injectors.opencti.OpenCTIInjectorIntegration.OPENCTI_INJECTOR_ID;
import static io.openaev.integration.impl.injectors.opencti.OpenCTIInjectorIntegration.OPENCTI_INJECTOR_NAME;

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
@ConfigurationProperties(prefix = "injector.opencti")
public class OpenCTIInjectorConfig extends BaseIntegrationConfiguration {
  @IntegrationConfigKey(
      key = "INJECTOR_ID",
      description =
          """
            ID of the builtin Opencti injector
          """,
      isRequired = true)
  @NotBlank
  private String id = OPENCTI_INJECTOR_ID;

  @IntegrationConfigKey(
      key = "INJECTOR_NAME",
      description =
          """
            Name of the builtin opencti injector
          """,
      isRequired = true)
  @NotBlank
  private String name = OPENCTI_INJECTOR_NAME;
}
