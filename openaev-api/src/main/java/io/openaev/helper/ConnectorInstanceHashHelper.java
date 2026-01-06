package io.openaev.helper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConnectorInstanceHashHelper {

  private ConnectorInstanceHashHelper() {}

  public static String computeInstanceHash(ConnectorInstance instance) {
    if (instance == null) {
      throw new IllegalArgumentException("ConnectorInstance cannot be null");
    }

    String identity = computeConnectorIdentity(instance.getCatalogConnector());
    String config = transformConfigurationsToString(instance.getConfigurations());

    String dataToHash = String.format("%s|CONFIG[%s]", identity, config);
    return hashWithSHA256(dataToHash);
  }

  // Identity
  private static String computeConnectorIdentity(CatalogConnector catalogConnector) {
    if (catalogConnector == null) {
      return "UNKNOWN";
    }

    // External connector
    if (catalogConnector.getContainerImage() != null
        && !catalogConnector.getContainerImage().isBlank()) {
      return String.format(
          "IMAGE[%s:%s]",
          catalogConnector.getContainerImage(), catalogConnector.getContainerVersion());
    }

    // Built-in connector
    return String.format("BUILTIN[%s]", catalogConnector.getClassName());
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

  // Hashing
  private static String hashWithSHA256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

      // Convert byte array to hex string
      StringBuilder hexString = new StringBuilder(2 * hash.length);
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
