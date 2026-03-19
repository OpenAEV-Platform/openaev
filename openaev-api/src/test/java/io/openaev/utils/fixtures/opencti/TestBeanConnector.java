package io.openaev.utils.fixtures.opencti;

import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import java.util.UUID;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class TestBeanConnector extends ConnectorBase {
  private final String name = "Test Bean Connector";
  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;

  @Value("${openaev.test.connector.url:#{'test opencti server url'}}")
  private String url;

  public TestBeanConnector() {
    this.setAuto(false);
    this.setOnlyContextual(false);
    this.setPlaybookCompatible(false);
    this.setScope(null);
    this.setListenCallbackURI("test callback uri");
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public String getApiUrl() {
    return url;
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
