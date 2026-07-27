package io.openaev.helper;

/** Utility methods to build tenant and admin URLs from the platform base URL. */
public final class UrlHelper {

  private UrlHelper() {}

  public static String buildTenantUrl(String baseUrl, String tenantId) {
    return baseUrl + "/" + tenantId;
  }

  public static String buildFrontScenarioUrl(String baseUrl, String tenantId, String scenarioId) {
    return buildFrontResourceUrl(baseUrl, tenantId, "scenarios", scenarioId);
  }

  public static String buildFrontSimulationUrl(
      String baseUrl, String tenantId, String simulationId) {
    return buildFrontResourceUrl(baseUrl, tenantId, "simulations", simulationId);
  }

  private static String buildFrontResourceUrl(
      String baseUrl, String tenantId, String resourcePath, String resourceId) {
    return buildTenantUrl(baseUrl, tenantId) + "/admin/" + resourcePath + "/" + resourceId;
  }
}
