package io.openaev.database.raw;

/**
 * Spring Data projection interface for tenant data.
 *
 * <p>This interface defines a projection for retrieving tenant id. It serves as a base projection
 * for tenant-related queries and can be extended with additional fields as needed.
 *
 * @see io.openaev.database.model.Tenant
 */
public interface RawTenant {

  /**
   * Returns the unique identifier of the tenant.
   *
   * @return the tenant ID
   */
  String getTenant_id();
}
