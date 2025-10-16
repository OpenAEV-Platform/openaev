package io.openaev.utils.fixtures.opencti;

import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class ConnectorFixture {
  @Getter
  @RequiredArgsConstructor
  private static class TestConnector extends ConnectorBase {
    private final String name;
    private final ConnectorType type;

    @Override
    public String getApiUrl() {
      return getUrl();
    }
  }

  public static ConnectorBase getDefaultConnector() {
    ConnectorBase cb = new TestConnector("Test connector", ConnectorType.INTERNAL_ENRICHMENT);
    cb.setId(UUID.randomUUID().toString());
    cb.setUrl("test opencti server url");
    cb.setAuthToken(UUID.randomUUID().toString());
    cb.setAuto(false);
    cb.setAutoUpdate(false);
    cb.setOnlyContextual(false);
    cb.setPlaybookCompatible(false);
    cb.setScope(new ArrayList<>(List.of("scope_1", "scope_2")));
    cb.setListenCallbackURI("test callback uri");
    return cb;
  }
}
