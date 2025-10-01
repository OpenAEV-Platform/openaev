package io.openaev.utils.fixtures;

import io.openaev.database.model.SecurityCoverageSendJob;

public class SecurityCoverageSendJobFixture {
  public static SecurityCoverageSendJob createDefaultSecurityCoverageSendJob() {
    SecurityCoverageSendJob securityCoverageSendJob = new SecurityCoverageSendJob();
    securityCoverageSendJob.setStatus("PENDING");
    return securityCoverageSendJob;
  }
}
