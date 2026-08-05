package io.openaev.secrets.provider;

public enum SecretsProviderType {
  LOCAL("openaev_local_secret_provider"),
  PLACEHOLDER("placeholder");

  public final String type;

  SecretsProviderType(String type) {
    this.type = type;
  }
}
