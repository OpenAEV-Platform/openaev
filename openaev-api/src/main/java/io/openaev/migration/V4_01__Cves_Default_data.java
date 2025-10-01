package io.openaev.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_01__Cves_Default_data extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();

    try (PreparedStatement insertCve =
            connection.prepareStatement(
                "INSERT INTO cves (cve_id, cve_external_id, cve_source_identifier, cve_published, "
                    + "cve_description, cve_cvss, cve_cisa_exploit_add, cve_cisa_action_due, "
                    + "cve_cisa_required_action, cve_cisa_vulnerability_name) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (cve_id) DO NOTHING");
        PreparedStatement insertCwe =
            connection.prepareStatement(
                "INSERT INTO cwes (cwe_id, cwe_external_id, cwe_source) "
                    + "VALUES (?, ?, ?) ON CONFLICT (cwe_id) DO NOTHING");
        PreparedStatement linkCveCwe =
            connection.prepareStatement(
                "INSERT INTO cves_cwes (cve_id, cwe_id) VALUES (?, ?) ON CONFLICT DO NOTHING");
        PreparedStatement insertRef =
            connection.prepareStatement(
                "INSERT INTO cve_reference_urls (cve_id, cve_reference_url) "
                    + "VALUES (?, ?) ON CONFLICT DO NOTHING")) {
      // --- Helper method for bulk insertion ---
      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2021-26855",
          "2021-03-02T00:00:00Z",
          "Microsoft Exchange Server ProxyLogon SSRF leading to RCE.",
          "9.8",
          "Apply updates per vendor instructions",
          "Microsoft Exchange Server Remote Code Execution Vulnerability",
          "CWE-918",
          "NVD",
          new String[] {
            "https://portal.msrc.microsoft.com/en-US/security-guidance/advisory/CVE-2021-26855",
            "http://packetstormsecurity.com/files/161938/Microsoft-Exchange-ProxyLogon-Remote-Code-Execution.html",
            "https://nvd.nist.gov/vuln/detail/CVE-2021-26855"
          });

      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2023-20198",
          "2023-10-16T00:00:00Z",
          "Cisco IOS XE Web UI remote code execution via unauthenticated command injection.",
          "10.0",
          "Verify compliance with BOD 23‑02 and apply mitigations.",
          "Cisco IOS XE Web UI Privilege Escalation Vulnerability",
          "CWE-420",
          "NVD",
          new String[] {
            "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z",
            "https://nvd.nist.gov/vuln/detail/CVE-2023-20198",
            "https://github.com/W01fh4cker/CVE-2023-20198-RCE"
          });

      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2023-46805",
          "2024-01-12T00:00:00Z",
          "Ivanti Connect Secure/Policy Secure gateway authentication bypass.",
          "8.2",
          "Apply mitigations per vendor instructions or discontinue product if unavailable.",
          "Ivanti Connect Secure and Policy Secure Authentication Bypass Vulnerability",
          "CWE-287",
          "NVD",
          new String[] {
            "https://nvd.nist.gov/vuln/detail/CVE-2023-46805",
            "https://www.twingate.com/blog/tips/cve-2023-46805"
          });

      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2023-48788",
          "2023-12-01T00:00:00Z",
          "Improper neutralization of SQL elements in Fortinet FortiClientEMS allows RCE/commands.",
          "9.8",
          "Apply mitigations per vendor instructions or discontinue product if unavailable.",
          "Fortinet FortiClient EMS SQL Injection Vulnerability",
          "CWE-789",
          "NVD",
          new String[] {"https://nvd.nist.gov/vuln/detail/CVE-2023-48788"});

      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2024-20353",
          "2019-06-10T00:00:00Z",
          "Use-after-free in mongoose.c (mg_http_get_proto_data) leading to DoS or RCE.",
          "8.6",
          "Apply mitigations per vendor instructions or discontinue product if unavailable.",
          "Cisco ASA and FTD Denial of Service Vulnerability",
          "CWE-416",
          "MITRE",
          new String[] {
            "https://github.com/insi2304/mongoose-6.13-fuzz/blob/master/Simplest_Web_Server_Use_After_Free-read-mg_http_get_proto_data5932.png"
          });

      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2018-0171",
          "2018-05-14T00:00:00Z",
          "Cisco Smart Install buffer overflow leading to RCE/DoS.",
          "9.8",
          "Apply mitigations per vendor instructions or discontinue product if unavailable.",
          "Cisco IOS and IOS XE Software Smart Install Remote Code Execution Vulnerability",
          "CWE-787",
          "Out-of-bounds Write",
          new String[] {"https://nvd.nist.gov/vuln/detail/CVE-2018-0171"});

      insertEntry(
          insertCve,
          insertCwe,
          linkCveCwe,
          insertRef,
          "CVE-2023-20273",
          "2023-10-16T00:00:00Z",
          "Cisco IOS XE Web UI insufficient input validation leading to root command injection.",
          "7.2",
          "Verify compliance with BOD 23‑02 and apply mitigations.",
          "Cisco IOS XE Web UI Command Injection Vulnerability",
          "CWE-78",
          "Nist",
          new String[] {
            "https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-iosxe-webui-privesc-j22SaA4z"
          });
    }
  }

  private void insertEntry(
      PreparedStatement insertCve,
      PreparedStatement insertCwe,
      PreparedStatement linkCveCwe,
      PreparedStatement insertRef,
      String cveString,
      String publishedTs,
      String description,
      String cvss,
      String requiredAction,
      String vulnerabilityName,
      String cweString,
      String cweSource,
      String[] refUrls)
      throws Exception {
    String cveId = UUID.randomUUID().toString();
    String cweId = UUID.randomUUID().toString();

    insertCve.setString(1, cveId);
    insertCve.setString(2, cveString);
    insertCve.setString(3, cveString);
    insertCve.setTimestamp(4, java.sql.Timestamp.from(java.time.Instant.parse(publishedTs)));
    insertCve.setString(5, description);
    insertCve.setBigDecimal(6, new java.math.BigDecimal(cvss));
    insertCve.setTimestamp(7, java.sql.Timestamp.from(java.time.Instant.parse(publishedTs)));
    insertCve.setTimestamp(8, java.sql.Timestamp.from(java.time.Instant.parse(publishedTs)));
    insertCve.setString(9, requiredAction);
    insertCve.setString(10, vulnerabilityName);
    insertCve.executeUpdate();

    insertCwe.setString(1, cweId);
    insertCwe.setString(2, cweString);
    insertCwe.setString(3, cweSource);
    insertCwe.executeUpdate();

    linkCveCwe.setString(1, cveId);
    linkCveCwe.setString(2, cweId);
    linkCveCwe.executeUpdate();

    for (String url : refUrls) {
      insertRef.setString(1, cveId);
      insertRef.setString(2, url);
      insertRef.executeUpdate();
    }
  }
}
