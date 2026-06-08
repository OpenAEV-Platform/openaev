package io.openaev.secrets.provider.impl.vault.api;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.secrets.provider.impl.vault.VaultSecretProviderConfig;
import java.io.IOException;
import java.util.List;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public class VaultClient {
  private final HttpClientFactory httpClientFactory;
  private final VaultSecretProviderConfig config;

  public VaultClient(HttpClientFactory httpClientFactory, VaultSecretProviderConfig config) {
    this.httpClientFactory = httpClientFactory;
    this.config = config;
  }

  public List<String> enabledEngines() throws IOException {
    List<String> enabledEngines = List.of();
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {}

    return enabledEngines;
  }
}
