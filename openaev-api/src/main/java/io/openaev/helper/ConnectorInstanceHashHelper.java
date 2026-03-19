package io.openaev.helper;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConnectorInstanceHashHelper {

  private ConnectorInstanceHashHelper() {}

  public static String computeInstanceHash(ConnectorInstance instance) {
    if (instance == null) {
      throw new IllegalArgumentException("ConnectorInstance cannot be null");
    }

    String identity = instance.getHashIdentity();
    String config = transformConfigurationsToString(instance.getConfigurations());

    String dataToHash = String.format("%s|CONFIG[%s]", identity, config);
    return hashWithSHA256(dataToHash);
  }

  // Configuration normalization
  private static String transformConfigurationsToString(
      Set<ConnectorInstanceConfiguration> configurations) {

    if (configurations == null || configurations.isEmpty()) {
      return "";
    }

    return configurations.stream()
        .filter(c -> c != null && c.getKey() != null && c.getValue() != null)
        .sorted(Comparator.comparing(ConnectorInstanceConfiguration::getKey))
        .map(c -> String.format("%s=%s", c.getKey(), c.getValue()))
        .collect(Collectors.joining(";"));
  }
}
