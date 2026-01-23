package io.openaev.utils.fixtures;

import io.openaev.database.model.SecurityPlatform;

public class SecurityPlatformFixture {
  public static SecurityPlatform createDefault(String name, String type) {
    SecurityPlatform edr = new SecurityPlatform();
    edr.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.valueOf(type));
    edr.setName(name);
    edr.setDescription("I don't see anything, hear anything, say anything");
    return edr;
  }
}
