package io.openaev.executors.sentinelone.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "executor.sentinelone")
public class SentinelOneExecutorConfig {

  private static final String API_URI = "/web/api/v2.1/";

  @Getter private boolean enable;

  @Getter @NotBlank private String id;

  @Getter @NotBlank private String url;

  @Getter @NotBlank private String apiKey;

  @Getter @NotBlank private Integer apiBatchExecutionActionPagination = 2500;

  @Getter @NotBlank private Integer apiRegisterInterval = 1200;

  @Getter @NotBlank private String accountId;

  @Getter @NotBlank private String siteId;

  @Getter @NotBlank private String groupId;

  @Getter @NotBlank private String windowsScriptId;

  @Getter @NotBlank private String unixScriptId;

  public String getApiUrl() {
    return url + API_URI;
  }
}
