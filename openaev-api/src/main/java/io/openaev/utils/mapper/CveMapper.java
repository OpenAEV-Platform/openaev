package io.openaev.utils.mapper;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.Cve;
import io.openaev.database.model.Cwe;
import io.openaev.database.model.Vulnerability;
import io.openaev.ee.Ee;
import io.openaev.rest.cve.form.CveOutput;
import io.openaev.rest.cve.form.CveSimple;
import io.openaev.rest.vulnerability.form.CweOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class CveMapper {

  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;

  private Cve.VulnerabilityStatus mapVulnerabilityStatus(Vulnerability.VulnerabilityStatus status) {
    if (status == null) {
      return null;
    }
    return Cve.VulnerabilityStatus.valueOf(status.name());
  }

  public CveSimple toCveSimple(final Vulnerability vulnerability) {
    if (vulnerability == null) {
      return null;
    }
    return CveSimple.builder()
        .id(vulnerability.getId())
        .externalId(vulnerability.getExternalId())
        .cvssV31(vulnerability.getCvssV31())
        .published(vulnerability.getPublished())
        .build();
  }

  public CveOutput toCveOutput(final Vulnerability vulnerability) {
    if (vulnerability == null) {
      return null;
    }
    return CveOutput.builder()
        .id(vulnerability.getId())
        .externalId(vulnerability.getExternalId())
        .cvssV31(vulnerability.getCvssV31())
        .published(vulnerability.getPublished())
        .sourceIdentifier(vulnerability.getSourceIdentifier())
        .description(vulnerability.getDescription())
        .vulnStatus(mapVulnerabilityStatus(vulnerability.getVulnStatus()))
        .cisaActionDue(vulnerability.getCisaActionDue())
        .cisaExploitAdd(vulnerability.getCisaExploitAdd())
        .cisaRequiredAction(vulnerability.getCisaRequiredAction())
        .cisaVulnerabilityName(vulnerability.getCisaVulnerabilityName())
        .remediation(getRemediationIfLicensed(vulnerability))
        .referenceUrls(new ArrayList<>(vulnerability.getReferenceUrls()))
        .cwes(toCweOutputs(vulnerability.getCwes()))
        .build();
  }

  private List<CweOutput> toCweOutputs(final List<Cwe> cwes) {
    if (cwes == null || cwes.isEmpty()) {
      return Collections.emptyList();
    }
    return cwes.stream().map(this::toCweOutput).collect(Collectors.toList());
  }

  public CweOutput toCweOutput(final Cwe cwe) {
    if (cwe == null) {
      return null;
    }
    return CweOutput.builder().externalId(cwe.getExternalId()).source(cwe.getSource()).build();
  }

  private String getRemediationIfLicensed(final Vulnerability vulnerability) {
    if (eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return vulnerability.getRemediation();
    } else {
      log.debug("Enterprise Edition license inactive - omitting remediation field");
      return null;
    }
  }
}
