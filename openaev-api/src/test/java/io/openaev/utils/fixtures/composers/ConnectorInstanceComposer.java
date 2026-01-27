package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.ConnectorInstanceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConnectorInstanceComposer extends ComposerBase<ConnectorInstancePersisted> {
  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;

  public class Composer extends InnerComposerBase<ConnectorInstancePersisted> {
    private final ConnectorInstancePersisted connectorInstance;
    private final List<ConnectorInstanceConfigurationComposer.Composer>
        connectorInstanceConfigurationComposer = new ArrayList<>();
    private Optional<CatalogConnectorComposer.Composer> catalogConnectorComposer = Optional.empty();

    public Composer(ConnectorInstancePersisted connectorInstance) {
      this.connectorInstance = connectorInstance;
    }

    public Composer withConnectorInstanceConfiguration(
        ConnectorInstanceConfigurationComposer.Composer configurationComposer) {
      this.connectorInstanceConfigurationComposer.add(configurationComposer);
      Set<ConnectorInstanceConfiguration> tempConfigurations =
          this.connectorInstance.getConfigurations();
      tempConfigurations.add(configurationComposer.get());
      configurationComposer.get().setConnectorInstance(connectorInstance);
      this.connectorInstance.setConfigurations(tempConfigurations);
      return this;
    }

    public Composer withCatalogConnector(CatalogConnectorComposer.Composer catalogConnector) {
      this.catalogConnectorComposer = Optional.of(catalogConnector);
      this.connectorInstance.setCatalogConnector(catalogConnector.get());
      return this;
    }

    @Override
    public ConnectorInstanceComposer.Composer persist() {
      catalogConnectorComposer.ifPresent(CatalogConnectorComposer.Composer::persist);
      connectorInstanceRepository.save(connectorInstance);
      connectorInstanceConfigurationComposer.forEach(
          ConnectorInstanceConfigurationComposer.Composer::persist);
      return this;
    }

    @Override
    public ConnectorInstanceComposer.Composer delete() {
      catalogConnectorComposer.ifPresent(CatalogConnectorComposer.Composer::delete);
      connectorInstanceRepository.delete(this.connectorInstance);
      connectorInstanceConfigurationComposer.forEach(
          ConnectorInstanceConfigurationComposer.Composer::delete);
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
