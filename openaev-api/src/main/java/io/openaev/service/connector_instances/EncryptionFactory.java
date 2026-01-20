package io.openaev.service.connector_instances;

import io.openaev.database.model.CatalogConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EncryptionFactory {

  private final XtmComposerEncryptionService xtmComposerEncryptionService;
  private final NativeEncryptionService nativeEncryptionService;

  /**
   * Gets the appropriate encryption strategy based on catalog connector type.
   *
   * @param catalogConnector the catalog connector
   * @return the encryption strategy, or null if no encryption needed
   */
  public EncryptionService getEncryptionService(CatalogConnector catalogConnector) {
    if (catalogConnector.isManagerSupported()) {
      return xtmComposerEncryptionService;
    }
    return nativeEncryptionService;
  }
}
