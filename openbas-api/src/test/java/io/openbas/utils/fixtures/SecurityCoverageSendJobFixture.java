package io.openbas.utils.fixtures;

import io.openbas.database.model.SecurityCoverageSendJob;

public class SecurityCoverageSendJobFixture {
  public static SecurityCoverageSendJob createDefaultSecurityCoverageSendJob() {
    SecurityCoverageSendJob securityCoverageSendJob = new SecurityCoverageSendJob();
    securityCoverageSendJob.setStatus("PENDING");
    return securityCoverageSendJob;
  }
}
