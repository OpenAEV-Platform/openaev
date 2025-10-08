package io.openaev.opencti.connectors.impl;

import static io.openaev.opencti.config.OpenCTIConfig.GRAPHQL_ENDPOINT_URI;

import io.openaev.api.stix_process.StixApi;
import io.openaev.config.OpenAEVConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@ConfigurationProperties(prefix = "openaev.xtm.opencti.connector.security-coverage")
public class SecurityCoverageConnector extends ConnectorBase {
  // need to access the base URL for overriding the callback URI
  @Autowired private OpenAEVConfig mainConfig;

  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;
  private final String name = "OpenAEV Coverage";

  public SecurityCoverageConnector() {
    this.setScope(new ArrayList<>(List.of("application/stix+json;version=2.1", "indicator")));
  }

  @Override
  public String getUrl() {
    if (io.openaev.utils.StringUtils.isBlank(super.getUrl())) {
      return null;
    }

    String configuredStripped = StringUtils.stripEnd(super.getUrl(), "/");
    if (configuredStripped.endsWith("/%s".formatted(GRAPHQL_ENDPOINT_URI))) {
      return configuredStripped;
    }
    return configuredStripped + "/graphql";
  }

  @Override
  public String getListenCallbackURI() {
    return mainConfig.getBaseUrl() + StixApi.STIX_URI + "/process-bundle";
  }
}
