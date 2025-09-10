package io.openbas.utils.fixtures;

import io.openbas.database.model.Cve;
import java.math.BigDecimal;

public class CveFixture {

  public static final String CVE_2023_48788 = "CVE-2023-48788";
  public static final String CVE_2023_20198 = "CVE-2023-20198";

  public static Cve createDefaultCve(String externalId) {
    Cve cve = new Cve();
    cve.setCvssV31(new BigDecimal("10.0"));
    cve.setExternalId(externalId);
    return cve;
  }
}
