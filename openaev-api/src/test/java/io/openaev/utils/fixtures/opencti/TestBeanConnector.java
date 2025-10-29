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
    this.setAuto(false);
    this.setOnlyContextual(false);
    this.setPlaybookCompatible(false);
    this.setScope(null);
    this.setListenCallbackURI("test callback uri");
  }

  @Override
  public String getUrl() {
    return "test opencti server url";
  }

  @Override
  public String getApiUrl() {
    return "test opencti server url";
  }

  @Override
  public String getId() {
    return UUID.randomUUID().toString();
  }

  @Override
  public String getToken() {
    return UUID.randomUUID().toString();
  }

  @Override
  public boolean shouldRegister() {
    return false;
  }
}
