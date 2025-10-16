package io.openaev.utils.fixtures.opencti;

import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import java.util.UUID;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class TestBeanConnector extends ConnectorBase {
  private final String name = "Test Bean Connector";
  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;

  public TestBeanConnector() {
    this.setId(UUID.randomUUID().toString());
    this.setUrl("test opencti server url");
    this.setAuthToken(UUID.randomUUID().toString());
    this.setAuto(false);
    this.setOnlyContextual(false);
    this.setPlaybookCompatible(false);
    this.setScope(null);
    this.setListenCallbackURI("test callback uri");
  }

  @Override
  public String getApiUrl() {
    return getUrl();
  }
}
