package io.openbas.utils.fixtures;

import io.openbas.database.model.Cve;
import java.math.BigDecimal;
import java.util.UUID;

public class CveFixture {
  public static Cve createDefaultCve() {
    Cve cve = new Cve();
    cve.setCvssV31(new BigDecimal("10.0"));
    cve.setExternalId("CVE-%s".formatted(UUID.randomUUID()));
    return cve;
  }
}
