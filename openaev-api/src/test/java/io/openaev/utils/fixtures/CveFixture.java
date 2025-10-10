package io.openaev.utils.fixtures;

import io.openaev.database.model.Vulnerability;
import java.math.BigDecimal;
import java.util.UUID;

public class CveFixture {

  public static final String CVE_2023_48788 = "CVE-2023-48788";
  public static final String CVE_2025_5678 = "CVE-2025-5678";

  public static Vulnerability createDefaultCve(String externalId) {
    Vulnerability vulnerability = new Vulnerability();
    vulnerability.setCvssV31(new BigDecimal("10.0"));
    vulnerability.setExternalId(externalId);
    return vulnerability;
  }

  public static String getRandomExternalVulnerabilityId() {
    return "CVE-%s".formatted(UUID.randomUUID());
  }
}
