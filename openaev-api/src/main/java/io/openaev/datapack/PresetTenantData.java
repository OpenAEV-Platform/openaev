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

  /**
   * Creates fresh {@link VulnerabilityCwe} instances for each call. Must not be a static field
   * because JPA-managed entities retain persistence state (version, managed status) after the first
   * save — reusing the same objects for a second tenant causes {@code
   * OptimisticLockingFailureException}.
   */
  private static Vulnerability createVuln(
      String extId,
      String published,
      String desc,
      String cvss,
      String action,
      String name,
      List<String> urls) {
    Vulnerability v = Vulnerability.fromTenant(null);
    v.setExternalId(extId);
    v.setSourceIdentifier(extId);
    v.setPublished(Instant.parse(published));
    v.setCisaExploitAdd(Instant.parse(published));
    v.setCisaActionDue(Instant.parse(published));
    v.setDescription(desc);
    v.setCvssV31(new BigDecimal(cvss));
    v.setVulnStatus(Vulnerability.VulnerabilityStatus.ANALYZED);
    v.setCisaRequiredAction(action);
    v.setCisaVulnerabilityName(name);
    v.setReferenceUrls(urls);
    return v;
  }

  private static Cwe createCwe(String extId, String source) {
    Cwe cwe = Cwe.fromTenant(null);
    cwe.setExternalId(extId);
    cwe.setSource(source);
    return cwe;
  }

  public static List<VulnerabilityCwe> createDefaultVulnerabilityCwes() {
    return List.of(
        new VulnerabilityCwe(
            createVuln(
                "CVE-2021-26855",
                "2021-03-02T00:00:00Z",
                "Microsoft Exchange Server ProxyLogon SSRF leading to RCE.",
                "9.8",
                "Apply updates per vendor instructions",
                "Microsoft Exchange Server Remote Code Execution Vulnerability",
                List.of(
                    "https://portal.msrc.microsoft.com/en-US/security-guidance/advisory/CVE-2021-26855",
                    "http://packetstormsecurity.com/files/161938/Microsoft-Exchange-ProxyLogon-Remote-Code-Execution.html",
                    "https://nvd.nist.gov/vuln/detail/CVE-2021-26855")),
            createCwe("CWE-918", "NVD")),
        new VulnerabilityCwe(
            createVuln(
                "CVE-2023-20198",
                "2023-10-16T00:00:00Z",
                "Cisco IOS XE Web UI remote code execution via unauthenticated command injection.",
                "10.0",
                "Verify compliance with BOD 23-02 and apply mitigations.",
                "Cisco IOS XE Web UI Privilege Escalation Vulnerability",
                List.of(
                    "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z",
                    "https://nvd.nist.gov/vuln/detail/CVE-2023-20198",
                    "https://github.com/W01fh4cker/CVE-2023-20198-RCE")),
            createCwe("CWE-420", "NVD")),
        new VulnerabilityCwe(
            createVuln(
                "CVE-2023-46805",
                "2024-01-12T00:00:00Z",
                "Ivanti Connect Secure/Policy Secure gateway authentication bypass.",
                "8.2",
                "Apply mitigations per vendor instructions or discontinue product if unavailable.",
                "Ivanti Connect Secure and Policy Secure Authentication Bypass Vulnerability",
                List.of(
                    "https://nvd.nist.gov/vuln/detail/CVE-2023-46805",
                    "https://www.twingate.com/blog/tips/cve-2023-46805")),
            createCwe("CWE-287", "NVD")),
        new VulnerabilityCwe(
            createVuln(
                "CVE-2023-48788",
                "2023-12-01T00:00:00Z",
                "Improper neutralization of SQL elements in Fortinet FortiClientEMS allows RCE/commands.",
                "9.8",
                "Apply mitigations per vendor instructions or discontinue product if unavailable.",
                "Fortinet FortiClient EMS SQL Injection Vulnerability",
                List.of("https://nvd.nist.gov/vuln/detail/CVE-2023-48788")),
            createCwe("CWE-789", "NVD")),
        new VulnerabilityCwe(
            createVuln(
                "CVE-2024-20353",
                "2019-06-10T00:00:00Z",
                "Use-after-free in mongoose.c (mg_http_get_proto_data) leading to DoS or RCE.",
                "8.6",
                "Apply mitigations per vendor instructions or discontinue product if unavailable.",
                "Cisco ASA and FTD Denial of Service Vulnerability",
                List.of(
                    "https://github.com/insi2304/mongoose-6.13-fuzz/blob/master/Simplest_Web_Server_Use_After_Free-read-mg_http_get_proto_data5932.png")),
            createCwe("CWE-416", "MITRE")),
        new VulnerabilityCwe(
            createVuln(
                "CVE-2018-0171",
                "2018-05-14T00:00:00Z",
                "Cisco Smart Install buffer overflow leading to RCE/DoS.",
                "9.8",
                "Apply mitigations per vendor instructions or discontinue product if unavailable.",
                "Cisco IOS and IOS XE Software Smart Install Remote Code Execution Vulnerability",
                List.of("https://nvd.nist.gov/vuln/detail/CVE-2018-0171")),
            createCwe("CWE-787", "Out-of-bounds Write")),
        new VulnerabilityCwe(
            createVuln(
                "CVE-2023-20273",
                "2023-10-16T00:00:00Z",
                "Cisco IOS XE Web UI insufficient input validation leading to root command injection.",
                "7.2",
                "Verify compliance with BOD 23-02 and apply mitigations.",
                "Cisco IOS XE Web UI Command Injection Vulnerability",
                List.of(
                    "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z")),
            createCwe("CWE-78", "Nist")));
  }

  public static final Map<String, Set<Capability>> DEFAULT_ROLES =
      Map.of(
          OBSERVER,
          Set.of(
              Capability.ACCESS_ASSESSMENT,
              Capability.ACCESS_ASSETS,
              Capability.ACCESS_THREAT_ARSENALS,
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
              Capability.ACCESS_THREAT_ARSENALS,
              Capability.MANAGE_THREAT_ARSENALS,
              Capability.DELETE_THREAT_ARSENALS,
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
