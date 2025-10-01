package io.openaev.executors.tanium.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "executor.tanium")
public class TaniumExecutorConfig {

  private static final String GATEWAY_URI = "/plugin/products/gateway/graphql";

  @Getter private boolean enable;

  @Getter @NotBlank private String id;

  @Getter @NotBlank private String url;

  @Getter @NotBlank private String apiKey;

  @Getter @NotBlank private Integer computerGroupId = 1;

  @Getter @NotBlank private Integer actionGroupId = 4;

  @Getter @NotBlank private Integer windowsPackageId;

  @Getter @NotBlank private Integer unixPackageId;

  public String getGatewayUrl() {
    return url + GATEWAY_URI;
  }
}
