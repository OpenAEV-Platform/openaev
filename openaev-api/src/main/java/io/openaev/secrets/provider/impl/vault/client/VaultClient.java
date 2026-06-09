package io.openaev.secrets.provider.impl.vault.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.secrets.provider.impl.vault.EngineType;
import io.openaev.secrets.provider.impl.vault.VaultSecretProviderConfig;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import io.openaev.secrets.provider.impl.vault.engine.aws.AwsEngine;
import io.openaev.secrets.provider.impl.vault.engine.azure.AzureEngine;
import io.openaev.secrets.provider.impl.vault.engine.gcp.GcpEngine;
import io.openaev.secrets.provider.impl.vault.engine.kv.KeyValueEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;

@Slf4j
public class VaultClient {
  private final HttpClientFactory httpClientFactory;
  private final VaultSecretProviderConfig config;
  private final ObjectMapper mapper;

  public VaultClient(
      HttpClientFactory httpClientFactory, VaultSecretProviderConfig config, ObjectMapper mapper) {
    this.httpClientFactory = httpClientFactory;
    this.config = config;
    this.mapper = mapper;
  }

  public List<Engine> enabledEngines() throws IOException {
    String url = "/v1/sys/mounts";

    List<Engine> enabledEngines = List.of();
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet get = new HttpGet(config.getUrl() + url);
      get.setHeader("X-Vault-Token", config.getAuthToken());
      enabledEngines =
          httpClient.execute(
              get,
              (ClassicHttpResponse response) -> {
                String responseJson =
                    EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JsonNode obj = mapper.readTree(responseJson);
                return obj.properties().stream()
                    .map(
                        entry -> {
                          try {
                            EngineType type =
                                EngineType.valueOf(
                                    entry.getValue().get("type").asText().toUpperCase());
                            switch (type) {
                              case EngineType.GCP -> {
                                Engine eng = new GcpEngine();
                                eng.setMountPoint(entry.getKey());
                                return eng;
                              }
                              case EngineType.AZURE -> {
                                Engine eng = new AzureEngine();
                                eng.setMountPoint(entry.getKey());
                                return eng;
                              }
                              case EngineType.AWS -> {
                                Engine eng = new AwsEngine();
                                eng.setMountPoint(entry.getKey());
                                return eng;
                              }
                              case EngineType.KV -> {
                                Engine eng = new KeyValueEngine();
                                eng.setMountPoint(entry.getKey());
                                return eng;
                              }
                              default -> {
                                return null;
                              }
                            }
                          } catch (Exception e) {
                            log.warn("skipping engine mounted at {}", entry.getKey());
                            return null;
                          }
                        })
                    .filter(Objects::nonNull)
                    .toList();
              });
    }

    return enabledEngines;
  }
}
