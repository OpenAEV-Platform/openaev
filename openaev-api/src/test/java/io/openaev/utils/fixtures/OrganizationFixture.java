package io.openaev.utils.fixtures;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import java.util.UUID;

public class OrganizationFixture {

  public static final String ORGANIZATION_FIXTURE_NAME = "Filigran test";

  // organizations is v2-native: no TenantBaseListener stamps tenant_id on persist anymore.
  private static void assignDefaultTenant(Organization organization) {
    organization.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
  }

  public static Organization createDefaultOrganisation() {
    Organization org = createOrganisationWithDefaultName();
    org.setDescription("Default organisation for tests");
    return org;
  }

  public static Organization createOrganization() {
    Organization organization = new Organization();
    organization.setName(ORGANIZATION_FIXTURE_NAME);
    organization.setDescription("Filigran test organization");
    assignDefaultTenant(organization);
    return organization;
  }

  private static Organization createOrganisationWithDefaultName() {
    return createOrganisationWithName(null);
  }

  private static Organization createOrganisationWithName(String name) {
    String new_name = name == null ? "organisation-%s".formatted(UUID.randomUUID()) : name;
    Organization organisation = new Organization();
    organisation.setName(new_name);
    assignDefaultTenant(organisation);
    return organisation;
  }
}
