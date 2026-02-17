package io.openaev.database.raw;

/**
 * Spring Data projection interface for custom dashboard data.
 *
 * <p>This interface defines a lightweight projection for retrieving custom dashboard identifiers
 * and names. Custom dashboards provide personalized views for exercises and scenarios.
 *
 * @see io.openaev.database.model.CustomDashboard
 */
public interface RawCustomDashboard {

  /**
   * Returns the unique identifier of the custom dashboard.
   *
   * @return the dashboard ID
   */
  String getCustom_dashboard_id();

  /**
   * Returns the display name of the custom dashboard.
   *
   * @return the dashboard name
   */
  String getCustom_dashboard_name();
}
