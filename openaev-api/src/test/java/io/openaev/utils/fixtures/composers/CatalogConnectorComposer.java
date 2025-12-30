package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.CatalogConnectorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CatalogConnectorComposer extends ComposerBase<CatalogConnector> {
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;

  public class Composer extends InnerComposerBase<CatalogConnector> {
    private final CatalogConnector catalogConnector;
    private final List<ConnectorInstanceComposer.Composer> connectorInstanceComposers =
        new ArrayList<>();
    private final List<CatalogConnectorConfigurationComposer.Composer>
        catalogConnectorConfigurationComposer = new ArrayList<>();

    public Composer(CatalogConnector catalogConnector) {
      this.catalogConnector = catalogConnector;
    }

    public Composer withConnectorInstance(
        ConnectorInstanceComposer.Composer connectorInstanceComposer) {
      connectorInstanceComposers.add(connectorInstanceComposer);
      Set<ConnectorInstancePersisted> tempInstances = catalogConnector.getInstances();
      tempInstances.add(connectorInstanceComposer.get());
      connectorInstanceComposer.get().setCatalogConnector(catalogConnector);
      catalogConnector.setInstances(tempInstances);
      return this;
    }

    public Composer withCatalogConnectorConfiguration(
        CatalogConnectorConfigurationComposer.Composer configurationComposer) {
      this.catalogConnectorConfigurationComposer.add(configurationComposer);
      Set<CatalogConnectorConfiguration> tempConfigurations =
          this.catalogConnector.getCatalogConnectorConfigurations();
      tempConfigurations.add(configurationComposer.get());
      configurationComposer.get().setCatalogConnector(catalogConnector);
      this.catalogConnector.setCatalogConnectorConfigurations(tempConfigurations);
      return this;
    }

    @Override
    public CatalogConnectorComposer.Composer persist() {
      catalogConnectorRepository.save(catalogConnector);
      connectorInstanceComposers.forEach(ConnectorInstanceComposer.Composer::persist);
      catalogConnectorConfigurationComposer.forEach(
          CatalogConnectorConfigurationComposer.Composer::persist);
      return this;
    }

    @Override
    public CatalogConnectorComposer.Composer delete() {
      connectorInstanceComposers.forEach(ConnectorInstanceComposer.Composer::delete);
      catalogConnectorRepository.delete(catalogConnector);
      catalogConnectorConfigurationComposer.forEach(
          CatalogConnectorConfigurationComposer.Composer::delete);
      return this;
    }

    @Override
    public CatalogConnector get() {
      return this.catalogConnector;
    }
  }

  public CatalogConnectorComposer.Composer forCatalogConnector(CatalogConnector catalogConnector) {
    generatedItems.add(catalogConnector);
    return new CatalogConnectorComposer.Composer(catalogConnector);
  }
}
