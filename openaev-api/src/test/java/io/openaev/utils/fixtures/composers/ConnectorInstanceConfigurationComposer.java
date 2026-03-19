package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConnectorInstanceConfigurationComposer
    extends ComposerBase<ConnectorInstanceConfiguration> {
  @Autowired
  private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  public class Composer extends InnerComposerBase<ConnectorInstanceConfiguration> {
    private final ConnectorInstanceConfiguration connectorInstanceConfiguration;

    public Composer(ConnectorInstanceConfiguration connectorInstanceConfiguration) {
      this.connectorInstanceConfiguration = connectorInstanceConfiguration;
    }

    @Override
    public ConnectorInstanceConfigurationComposer.Composer persist() {
      connectorInstanceConfigurationRepository.save(this.connectorInstanceConfiguration);
      return this;
    }

    @Override
    public ConnectorInstanceConfigurationComposer.Composer delete() {
      connectorInstanceConfigurationRepository.delete(this.connectorInstanceConfiguration);
      return this;
    }

    @Override
    public ConnectorInstanceConfiguration get() {
      return this.connectorInstanceConfiguration;
    }
  }

  public ConnectorInstanceConfigurationComposer.Composer forConnectorInstanceConfiguration(
      ConnectorInstanceConfiguration connectorInstanceConfiguration) {
    generatedItems.add(connectorInstanceConfiguration);
    return new ConnectorInstanceConfigurationComposer.Composer(connectorInstanceConfiguration);
  }
}
