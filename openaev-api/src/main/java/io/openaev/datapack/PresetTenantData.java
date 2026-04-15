package io.openaev.datapack;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Cwe;
import io.openaev.database.model.Vulnerability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PresetTenantData {

  public static final String ADMIN = "Admin";
  private static final String OBSERVER = "Observer";
  private static final String MANAGER = "Manager";

  public record VulnerabilityCwe(Vulnerability vulnerability, Cwe cwe) {}

  public static final List<VulnerabilityCwe> DEFAULT_VULNERABILITY_CWES =
      List.of(
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2021-26855")
                  .sourceIdentifier("CVE-2021-26855")
                  .published(Instant.parse("2021-03-02T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2021-03-02T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2021-03-02T00:00:00Z"))
                  .description("Microsoft Exchange Server ProxyLogon SSRF leading to RCE.")
                  .cvssV31(new BigDecimal("9.8"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction("Apply updates per vendor instructions")
                  .cisaVulnerabilityName(
                      "Microsoft Exchange Server Remote Code Execution Vulnerability")
                  .referenceUrls(
                      List.of(
                          "https://portal.msrc.microsoft.com/en-US/security-guidance/advisory/CVE-2021-26855",
                          "http://packetstormsecurity.com/files/161938/Microsoft-Exchange-ProxyLogon-Remote-Code-Execution.html",
                          "https://nvd.nist.gov/vuln/detail/CVE-2021-26855"))
                  .build(),
              Cwe.builder().externalId("CWE-918").source("NVD").build()),
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2023-20198")
                  .sourceIdentifier("CVE-2023-20198")
                  .published(Instant.parse("2023-10-16T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2023-10-16T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2023-10-16T00:00:00Z"))
                  .description(
                      "Cisco IOS XE Web UI remote code execution via unauthenticated command injection.")
                  .cvssV31(new BigDecimal("10.0"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction("Verify compliance with BOD 23-02 and apply mitigations.")
                  .cisaVulnerabilityName("Cisco IOS XE Web UI Privilege Escalation Vulnerability")
                  .referenceUrls(
                      List.of(
                          "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z",
                          "https://nvd.nist.gov/vuln/detail/CVE-2023-20198",
                          "https://github.com/W01fh4cker/CVE-2023-20198-RCE"))
                  .build(),
              Cwe.builder().externalId("CWE-420").source("NVD").build()),
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2023-46805")
                  .sourceIdentifier("CVE-2023-46805")
                  .published(Instant.parse("2024-01-12T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2024-01-12T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2024-01-12T00:00:00Z"))
                  .description("Ivanti Connect Secure/Policy Secure gateway authentication bypass.")
                  .cvssV31(new BigDecimal("8.2"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction(
                      "Apply mitigations per vendor instructions or discontinue product if unavailable.")
                  .cisaVulnerabilityName(
                      "Ivanti Connect Secure and Policy Secure Authentication Bypass Vulnerability")
                  .referenceUrls(
                      List.of(
                          "https://nvd.nist.gov/vuln/detail/CVE-2023-46805",
                          "https://www.twingate.com/blog/tips/cve-2023-46805"))
                  .build(),
              Cwe.builder().externalId("CWE-287").source("NVD").build()),
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2023-48788")
                  .sourceIdentifier("CVE-2023-48788")
                  .published(Instant.parse("2023-12-01T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2023-12-01T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2023-12-01T00:00:00Z"))
                  .description(
                      "Improper neutralization of SQL elements in Fortinet FortiClientEMS allows RCE/commands.")
                  .cvssV31(new BigDecimal("9.8"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction(
                      "Apply mitigations per vendor instructions or discontinue product if unavailable.")
                  .cisaVulnerabilityName("Fortinet FortiClient EMS SQL Injection Vulnerability")
                  .referenceUrls(List.of("https://nvd.nist.gov/vuln/detail/CVE-2023-48788"))
                  .build(),
              Cwe.builder().externalId("CWE-789").source("NVD").build()),
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2024-20353")
                  .sourceIdentifier("CVE-2024-20353")
                  .published(Instant.parse("2019-06-10T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2019-06-10T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2019-06-10T00:00:00Z"))
                  .description(
                      "Use-after-free in mongoose.c (mg_http_get_proto_data) leading to DoS or RCE.")
                  .cvssV31(new BigDecimal("8.6"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction(
                      "Apply mitigations per vendor instructions or discontinue product if unavailable.")
                  .cisaVulnerabilityName("Cisco ASA and FTD Denial of Service Vulnerability")
                  .referenceUrls(
                      List.of(
                          "https://github.com/insi2304/mongoose-6.13-fuzz/blob/master/Simplest_Web_Server_Use_After_Free-read-mg_http_get_proto_data5932.png"))
                  .build(),
              Cwe.builder().externalId("CWE-416").source("MITRE").build()),
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2018-0171")
                  .sourceIdentifier("CVE-2018-0171")
                  .published(Instant.parse("2018-05-14T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2018-05-14T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2018-05-14T00:00:00Z"))
                  .description("Cisco Smart Install buffer overflow leading to RCE/DoS.")
                  .cvssV31(new BigDecimal("9.8"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction(
                      "Apply mitigations per vendor instructions or discontinue product if unavailable.")
                  .cisaVulnerabilityName(
                      "Cisco IOS and IOS XE Software Smart Install Remote Code Execution Vulnerability")
                  .referenceUrls(List.of("https://nvd.nist.gov/vuln/detail/CVE-2018-0171"))
                  .build(),
              Cwe.builder().externalId("CWE-787").source("Out-of-bounds Write").build()),
          new VulnerabilityCwe(
              Vulnerability.builder()
                  .externalId("CVE-2023-20273")
                  .sourceIdentifier("CVE-2023-20273")
                  .published(Instant.parse("2023-10-16T00:00:00Z"))
                  .cisaExploitAdd(Instant.parse("2023-10-16T00:00:00Z"))
                  .cisaActionDue(Instant.parse("2023-10-16T00:00:00Z"))
                  .description(
                      "Cisco IOS XE Web UI insufficient input validation leading to root command injection.")
                  .cvssV31(new BigDecimal("7.2"))
                  .vulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED)
                  .cisaRequiredAction("Verify compliance with BOD 23-02 and apply mitigations.")
                  .cisaVulnerabilityName("Cisco IOS XE Web UI Command Injection Vulnerability")
                  .referenceUrls(
                      List.of(
                          "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z"))
                  .build(),
              Cwe.builder().externalId("CWE-78").source("Nist").build()));

  public static final Map<String, Set<Capability>> DEFAULT_ROLES =
      Map.of(
          OBSERVER,
          Set.of(
              Capability.ACCESS_ASSESSMENT,
              Capability.ACCESS_ASSETS,
              Capability.ACCESS_PAYLOADS,
              Capability.ACCESS_DASHBOARDS,
              Capability.ACCESS_FINDINGS,
              Capability.ACCESS_DOCUMENTS,
              Capability.ACCESS_CHANNELS,
              Capability.ACCESS_CHALLENGES,
              Capability.ACCESS_LESSONS_LEARNED,
              Capability.ACCESS_SECURITY_PLATFORMS),
          MANAGER,
          Set.of(
              Capability.ACCESS_ASSESSMENT,
              Capability.MANAGE_ASSESSMENT,
              Capability.DELETE_ASSESSMENT,
              Capability.LAUNCH_ASSESSMENT,
              Capability.MANAGE_TEAMS_AND_PLAYERS,
              Capability.DELETE_TEAMS_AND_PLAYERS,
              Capability.ACCESS_ASSETS,
              Capability.MANAGE_ASSETS,
              Capability.DELETE_ASSETS,
              Capability.ACCESS_PAYLOADS,
              Capability.MANAGE_PAYLOADS,
              Capability.DELETE_PAYLOADS,
              Capability.ACCESS_DASHBOARDS,
              Capability.MANAGE_DASHBOARDS,
              Capability.DELETE_DASHBOARDS,
              Capability.ACCESS_FINDINGS,
              Capability.MANAGE_FINDINGS,
              Capability.DELETE_FINDINGS,
              Capability.ACCESS_DOCUMENTS,
              Capability.MANAGE_DOCUMENTS,
              Capability.DELETE_DOCUMENTS,
              Capability.ACCESS_CHANNELS,
              Capability.MANAGE_CHANNELS,
              Capability.DELETE_CHANNELS,
              Capability.ACCESS_CHALLENGES,
              Capability.MANAGE_CHALLENGES,
              Capability.DELETE_CHALLENGES,
              Capability.ACCESS_LESSONS_LEARNED,
              Capability.MANAGE_LESSONS_LEARNED,
              Capability.DELETE_LESSONS_LEARNED,
              Capability.ACCESS_SECURITY_PLATFORMS,
              Capability.DELETE_SECURITY_PLATFORMS,
              Capability.MANAGE_SECURITY_PLATFORMS),
          ADMIN,
          Set.of(Capability.BYPASS));
}
