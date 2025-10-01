package io.openaev.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfig {

  @NotNull private String endpoint;

  @NotNull private String accessKey;

  @NotNull private String accessSecret;

  private int port = 9000;
  private String bucket = "openaev";
  private boolean secure = false;
}
