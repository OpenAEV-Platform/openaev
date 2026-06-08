package io.openaev.secrets.service;

import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ManagerFactory;
import io.openaev.secrets.provider.SecretsProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretService {
  private final ManagerFactory managerFactory;
  private List<SecretsProvider> providers;

  public List<SecretsProvider> getAllProviders() {
    this.providers =
        managerFactory
            .getManager()
            .requestManyAllStates(new ComponentRequest("secrets-provider"), SecretsProvider.class);
    return providers.stream().toList();
  }
}
