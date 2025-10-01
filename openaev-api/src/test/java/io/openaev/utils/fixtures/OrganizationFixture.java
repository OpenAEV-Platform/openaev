package io.openaev.utils.fixtures;

import io.openaev.database.model.Organization;
import java.util.UUID;

public class OrganizationFixture {

  public static final String ORGANIZATION_FIXTURE_NAME = "Filigran test";

  public static Organization createDefaultOrganisation() {
    Organization org = createOrganisationWithDefaultName();
    org.setDescription("Default organisation for tests");
    return org;
  }

  public static Organization createOrganization() {
    Organization organization = new Organization();
    organization.setName(ORGANIZATION_FIXTURE_NAME);
    organization.setDescription("Filigran test organization");
    return organization;
  }

  private static Organization createOrganisationWithDefaultName() {
    return createOrganisationWithName(null);
  }

  private static Organization createOrganisationWithName(String name) {
    String new_name = name == null ? "organisation-%s".formatted(UUID.randomUUID()) : name;
    Organization organisation = new Organization();
    organisation.setName(new_name);
    return organisation;
  }
}
