package io.openaev.utils.fixtures;

import io.openaev.rest.cve.form.CveCreateInput;
import java.math.BigDecimal;

public class CveInputFixture {

  public static final String CVE_EXTERNAL_ID = "CVE-2025-5679";
  public static final BigDecimal CVE_CVSS_V31 = new BigDecimal("4.5");
  public static final String CVE_DESCRIPTION = "Description";

  public static CveCreateInput createDefaultCveCreateInput() {
    CveCreateInput input = new CveCreateInput();
    input.setExternalId(CVE_EXTERNAL_ID);
    input.setCvssV31(CVE_CVSS_V31);
    input.setDescription(CVE_DESCRIPTION);
    return input;
  }
}
