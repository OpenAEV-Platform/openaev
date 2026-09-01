package io.openaev.database.model;

import com.azure.core.management.AzureEnvironment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable names for the Azure clouds exposed by the Azure SDK.
 *
 * <p>{@link AzureEnvironment} is a final class, not an enum, and exposes no name accessor: its
 * instances are only identified by their endpoints. This helper attaches the well-known cloud names
 * (the ones used by the Azure CLI and the {@code AZURE_ENVIRONMENT} variable) to those instances so
 * a single stable string can travel through the API, the form and the {@code
 * secret_azure_environment} column.
 */
public final class AzureEnvironments {

  private static final Map<String, AzureEnvironment> BY_NAME = new LinkedHashMap<>();

  static {
    BY_NAME.put("AzureCloud", AzureEnvironment.AZURE);
    BY_NAME.put("AzureChinaCloud", AzureEnvironment.AZURE_CHINA);
    BY_NAME.put("AzureUSGovernment", AzureEnvironment.AZURE_US_GOVERNMENT);
    BY_NAME.put("AzureGermanCloud", AzureEnvironment.AZURE_GERMANY);
  }

  private AzureEnvironments() {}

  /**
   * Lists the supported Azure cloud names, restricted to the environments the SDK still knows about
   * so a cloud retired upstream disappears from the form on the next SDK upgrade.
   *
   * @return supported Azure cloud names
   */
  public static List<String> names() {
    List<AzureEnvironment> known = AzureEnvironment.knownEnvironments();
    return BY_NAME.entrySet().stream()
        .filter(entry -> known.contains(entry.getValue()))
        .map(Map.Entry::getKey)
        .toList();
  }

  /**
   * Resolves an Azure cloud name to its SDK environment.
   *
   * @param name Azure cloud name
   * @return matching environment, or null if the name is blank
   * @throws IllegalArgumentException if the name is not a supported Azure cloud
   */
  public static AzureEnvironment fromName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    AzureEnvironment environment = BY_NAME.get(name.trim());
    if (environment == null) {
      throw new IllegalArgumentException("Unsupported Azure environment: " + name);
    }
    return environment;
  }
}
