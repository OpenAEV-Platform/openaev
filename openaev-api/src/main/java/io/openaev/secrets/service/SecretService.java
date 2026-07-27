package io.openaev.secrets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretService {
  //  private final ManagerFactory managerFactory;
  //  private List<SecretsProvider> providers;
  //
  //  public List<SecretsProvider> getAllProviders(String tenantId) {
  //    this.providers =
  //        managerFactory
  //            .getManager(tenantId)
  //            .requestManyAllStates(new ComponentRequest("secrets-provider"),
  // SecretsProvider.class);
  //    return providers.stream().toList();
  //  }
}
