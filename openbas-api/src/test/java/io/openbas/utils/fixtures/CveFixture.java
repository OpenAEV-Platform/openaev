package io.openbas.utils.fixtures;

import io.openbas.database.model.Cve;
import java.math.BigDecimal;
import java.util.UUID;

public class CveFixture {

  public static final String CVE_2023_48788 = "CVE-2023-48788";
  public static final String CVE_2025_5678 = "CVE-2025-5678";

  public static Cve createDefaultCve(String externalId) {
    Cve cve = new Cve();
    cve.setCvssV31(new BigDecimal("10.0"));
    cve.setExternalId(externalId);
    return cve;
  }

  public static String getRandomExternalVulnerabilityId() {
    return "CVE-%s".formatted(UUID.randomUUID());
  }
}
