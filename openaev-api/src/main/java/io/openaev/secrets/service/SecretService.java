package io.openaev.secrets.service;

import io.openaev.secrets.provider.SecretsProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretService {
  private final List<SecretsProvider> providers;

  public List<SecretsProvider> getAllProviders() {
    return providers.stream().toList();
  }
}
