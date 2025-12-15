package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.ConnectorInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConnectorInstanceComposer extends ComposerBase<ConnectorInstancePersisted> {
  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;

  public class Composer extends InnerComposerBase<ConnectorInstancePersisted> {
    private final ConnectorInstancePersisted connectorInstance;

    public Composer(ConnectorInstancePersisted connectorInstance) {
      this.connectorInstance = connectorInstance;
    }

    @Override
    public ConnectorInstanceComposer.Composer persist() {
      connectorInstanceRepository.save(connectorInstance);
      return this;
    }

    @Override
    public ConnectorInstanceComposer.Composer delete() {
      connectorInstanceRepository.delete(connectorInstance);
      return this;
    }

    @Override
    public ConnectorInstancePersisted get() {
      return this.connectorInstance;
    }
  }

  public ConnectorInstanceComposer.Composer forConnectorInstance(
      ConnectorInstancePersisted connectorInstance) {
    generatedItems.add(connectorInstance);
    return new ConnectorInstanceComposer.Composer(connectorInstance);
  }
}
