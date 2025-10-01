package io.openaev.injectors.caldera.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "injector.caldera")
public class CalderaInjectorConfig {

  public static final String PRODUCT_NAME = "Caldera";

  private static final String REST_V1_URI = "/api/rest";
  private static final String REST_V2_URI = "/api/v2";
  private static final String PLUGIN_ACCESS_URI = "/plugin/access";

  @Getter private boolean enable;

  @Getter @NotBlank private String id;

  @NotBlank private String url;

  @Getter @NotBlank private String publicUrl;

  @Getter @NotBlank private String apiKey;

  public String getRestApiV1Url() {
    return url + REST_V1_URI;
  }

  public String getRestApiV2Url() {
    return url + REST_V2_URI;
  }

  public String getPluginAccessApiUrl() {
    return url + PLUGIN_ACCESS_URI;
  }

  public String getUrl() {
    return url;
  }
}
