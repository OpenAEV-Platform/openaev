package io.openbas.utils.mapper;

import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.Cve;
import io.openbas.database.model.Cwe;
import io.openbas.ee.Ee;
import io.openbas.rest.cve.form.CveOutput;
import io.openbas.rest.cve.form.CveSimple;
import io.openbas.rest.cve.form.CweOutput;
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

  public CveSimple toCveSimple(final Cve cve) {
    if (cve == null) {
      return null;
    }
    return CveSimple.builder()
        .id(cve.getId())
        .externalId(cve.getExternalId())
        .cvss(cve.getCvss())
        .published(cve.getPublished())
        .build();
  }

  public CveOutput toCveOutput(final Cve cve) {
    if (cve == null) {
      return null;
    }
    return CveOutput.builder()
        .id(cve.getId())
        .externalId(cve.getExternalId())
        .cvss(cve.getCvss())
        .published(cve.getPublished())
        .sourceIdentifier(cve.getSourceIdentifier())
        .description(cve.getDescription())
        .vulnStatus(cve.getVulnStatus())
        .cisaActionDue(cve.getCisaActionDue())
        .cisaExploitAdd(cve.getCisaExploitAdd())
        .cisaRequiredAction(cve.getCisaRequiredAction())
        .cisaVulnerabilityName(cve.getCisaVulnerabilityName())
        .remediation(getRemediationIfLicensed(cve))
        .referenceUrls(new ArrayList<>(cve.getReferenceUrls()))
        .cwes(toCweOutputs(cve.getCwes()))
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

  private String getRemediationIfLicensed(final Cve cve) {
    if (eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return cve.getRemediation();
    } else {
      log.debug("Enterprise Edition license inactive - omitting remediation field");
      return null;
    }
  }
}
