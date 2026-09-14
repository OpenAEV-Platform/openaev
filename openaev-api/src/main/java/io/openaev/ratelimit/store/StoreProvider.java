package io.openaev.ratelimit.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreProvider {
  private final StoreFactory factory;
  private volatile Store storeBackend;

  public synchronized Store getStoreBackend() {
    if (storeBackend == null) {
      storeBackend = factory.fromConfiguration();
    }
    return storeBackend;
  }
}
