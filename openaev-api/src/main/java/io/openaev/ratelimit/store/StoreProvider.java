package io.openaev.ratelimit.store;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreProvider {
  private final StoreFactory factory;
  @Getter private Store storeBackend;

  @PostConstruct
  public void initStore() {
    this.storeBackend = factory.fromConfiguration();
  }
}
