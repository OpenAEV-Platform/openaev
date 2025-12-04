package io.openaev.executors.crowdstrike.config;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Setter
@Component
@ConfigurationProperties(prefix = "executor.crowdstrike")
public class CrowdStrikeExecutorConfig {

  @Getter private boolean enable;

  @Getter @NotBlank private String id;

  @Getter @NotBlank private String apiUrl;

  @Getter @NotBlank private Integer apiBatchExecutionActionPagination = 2500;

  @Getter @NotBlank private Integer apiRegisterInterval = 1200;

  @Getter @NotBlank private String clientId;

  @Getter @NotBlank private String clientSecret;

  @Getter @NotBlank private String hostGroup;

  @Getter @NotBlank private String windowsScriptName;

  @Getter @NotBlank private String unixScriptName;

  public Set<ConnectorInstanceConfiguration> toConnectorInstanceConfigurations(ConnectorInstance relatedInstance) {
    Set<ConnectorInstanceConfiguration> returned = new HashSet<>();
    return Set.of();
  }
}
