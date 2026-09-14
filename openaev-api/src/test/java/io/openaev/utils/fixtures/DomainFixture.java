package io.openaev.utils.fixtures;

import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import java.awt.*;
import java.util.UUID;

public class DomainFixture {
  public static Domain getRandomDomain() {
    return getDomainWithNameAndColour(
        UUID.randomUUID().toString(), ColourFixture.getRandomRgbString());
  }

  public static Domain getDomainWithNameAndColour(String name, String rgbColour) {
    Domain domain = new Domain();
    domain.setName(name);
    domain.setColor(rgbColour);
    domain.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return domain;
  }
}
