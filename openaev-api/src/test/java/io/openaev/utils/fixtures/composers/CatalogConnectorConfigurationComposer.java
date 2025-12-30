package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.repository.CatalogConnectorConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CatalogConnectorConfigurationComposer
    extends ComposerBase<CatalogConnectorConfiguration> {
  @Autowired
  private CatalogConnectorConfigurationRepository catalogConnectorConfigurationRepository;

  public class Composer extends InnerComposerBase<CatalogConnectorConfiguration> {
    private final CatalogConnectorConfiguration catalogConnectorConfiguration;

    public Composer(CatalogConnectorConfiguration catalogConnectorConfiguration) {
      this.catalogConnectorConfiguration = catalogConnectorConfiguration;
    }

    @Override
    public CatalogConnectorConfigurationComposer.Composer persist() {
      catalogConnectorConfigurationRepository.save(this.catalogConnectorConfiguration);
      return this;
    }

    @Override
    public CatalogConnectorConfigurationComposer.Composer delete() {
      catalogConnectorConfigurationRepository.delete(this.catalogConnectorConfiguration);
      return this;
    }

    @Override
    public CatalogConnectorConfiguration get() {
      return this.catalogConnectorConfiguration;
    }
  }

  public CatalogConnectorConfigurationComposer.Composer forCatalogConnectorConfiguration(
      CatalogConnectorConfiguration catalogConnectorConfiguration) {
    generatedItems.add(catalogConnectorConfiguration);
    return new CatalogConnectorConfigurationComposer.Composer(catalogConnectorConfiguration);
  }
}
