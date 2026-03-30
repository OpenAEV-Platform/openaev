package io.openaev.datapack;

import io.openaev.database.model.Capability;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PresetTenantData {

  public record DefaultVulnerability(
      String externalId,
      String published,
      String description,
      String cvss,
      String requiredAction,
      String vulnerabilityName,
      String cweExternalId,
      String cweSource,
      List<String> referenceUrls) {}

  public static final List<DefaultVulnerability> DEFAULT_VULNERABILITIES =
      List.of(
          new DefaultVulnerability(
              "CVE-2021-26855",
              "2021-03-02T00:00:00Z",
              "Microsoft Exchange Server ProxyLogon SSRF leading to RCE.",
              "9.8",
              "Apply updates per vendor instructions",
              "Microsoft Exchange Server Remote Code Execution Vulnerability",
              "CWE-918",
              "NVD",
              List.of(
                  "https://portal.msrc.microsoft.com/en-US/security-guidance/advisory/CVE-2021-26855",
                  "http://packetstormsecurity.com/files/161938/Microsoft-Exchange-ProxyLogon-Remote-Code-Execution.html",
                  "https://nvd.nist.gov/vuln/detail/CVE-2021-26855")),
          new DefaultVulnerability(
              "CVE-2023-20198",
              "2023-10-16T00:00:00Z",
              "Cisco IOS XE Web UI remote code execution via unauthenticated command injection.",
              "10.0",
              "Verify compliance with BOD 23-02 and apply mitigations.",
              "Cisco IOS XE Web UI Privilege Escalation Vulnerability",
              "CWE-420",
              "NVD",
              List.of(
                  "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z",
                  "https://nvd.nist.gov/vuln/detail/CVE-2023-20198",
                  "https://github.com/W01fh4cker/CVE-2023-20198-RCE")),
          new DefaultVulnerability(
              "CVE-2023-46805",
              "2024-01-12T00:00:00Z",
              "Ivanti Connect Secure/Policy Secure gateway authentication bypass.",
              "8.2",
              "Apply mitigations per vendor instructions or discontinue product if unavailable.",
              "Ivanti Connect Secure and Policy Secure Authentication Bypass Vulnerability",
              "CWE-287",
              "NVD",
              List.of(
                  "https://nvd.nist.gov/vuln/detail/CVE-2023-46805",
                  "https://www.twingate.com/blog/tips/cve-2023-46805")),
          new DefaultVulnerability(
              "CVE-2023-48788",
              "2023-12-01T00:00:00Z",
              "Improper neutralization of SQL elements in Fortinet FortiClientEMS allows RCE/commands.",
              "9.8",
              "Apply mitigations per vendor instructions or discontinue product if unavailable.",
              "Fortinet FortiClient EMS SQL Injection Vulnerability",
              "CWE-789",
              "NVD",
              List.of("https://nvd.nist.gov/vuln/detail/CVE-2023-48788")),
          new DefaultVulnerability(
              "CVE-2024-20353",
              "2019-06-10T00:00:00Z",
              "Use-after-free in mongoose.c (mg_http_get_proto_data) leading to DoS or RCE.",
              "8.6",
              "Apply mitigations per vendor instructions or discontinue product if unavailable.",
              "Cisco ASA and FTD Denial of Service Vulnerability",
              "CWE-416",
              "MITRE",
              List.of(
                  "https://github.com/insi2304/mongoose-6.13-fuzz/blob/master/Simplest_Web_Server_Use_After_Free-read-mg_http_get_proto_data5932.png")),
          new DefaultVulnerability(
              "CVE-2018-0171",
              "2018-05-14T00:00:00Z",
              "Cisco Smart Install buffer overflow leading to RCE/DoS.",
              "9.8",
              "Apply mitigations per vendor instructions or discontinue product if unavailable.",
              "Cisco IOS and IOS XE Software Smart Install Remote Code Execution Vulnerability",
              "CWE-787",
              "Out-of-bounds Write",
              List.of("https://nvd.nist.gov/vuln/detail/CVE-2018-0171")),
          new DefaultVulnerability(
              "CVE-2023-20273",
              "2023-10-16T00:00:00Z",
              "Cisco IOS XE Web UI insufficient input validation leading to root command injection.",
              "7.2",
              "Verify compliance with BOD 23-02 and apply mitigations.",
              "Cisco IOS XE Web UI Command Injection Vulnerability",
              "CWE-78",
              "Nist",
              List.of(
                  "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z")));

  public static final Map<String, Set<Capability>> DEFAULT_ROLES =
      Map.of(
          "Observer",
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
          "Manager",
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
          "Admin",
          Set.of(Capability.BYPASS));
}
