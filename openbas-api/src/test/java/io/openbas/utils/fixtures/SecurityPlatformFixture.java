package io.openbas.utils.fixtures;

import io.openbas.database.model.SecurityPlatform;

public class SecurityPlatformFixture {
  public static SecurityPlatform createDefaultEDR() {
    SecurityPlatform edr = new SecurityPlatform();
    edr.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
    edr.setName("A very bad EDR");
    edr.setDescription("I don't see anything, hear anything, say anything");
    return edr;
  }
}
