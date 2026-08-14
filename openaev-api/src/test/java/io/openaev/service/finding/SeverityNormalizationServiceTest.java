package io.openaev.service.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingSeverityBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SeverityNormalizationServiceTest {

  private final SeverityNormalizationService service = new SeverityNormalizationService();

  private Finding findingWith(ContractOutputType type, String severity) {
    Finding finding = new Finding();
    finding.setType(type);
    finding.setSeverity(severity);
    finding.setValue("test-value");
    return finding;
  }

  @Nested
  @DisplayName("Raw CVSS numeric score (Nuclei-style CVE findings)")
  class CvssScore {

    @Test
    @DisplayName("Should bucket 9.8 as CRITICAL")
    void given_cvss_9_8_should_bucket_critical() {
      Finding finding = findingWith(ContractOutputType.CVE, "9.8");
      assertEquals(FindingSeverityBucket.CRITICAL, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket 7.5 as HIGH")
    void given_cvss_7_5_should_bucket_high() {
      Finding finding = findingWith(ContractOutputType.CVE, "7.5");
      assertEquals(FindingSeverityBucket.HIGH, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket 5.4 as MEDIUM")
    void given_cvss_5_4_should_bucket_medium() {
      Finding finding = findingWith(ContractOutputType.CVE, "5.4");
      assertEquals(FindingSeverityBucket.MEDIUM, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket 2.1 as LOW")
    void given_cvss_2_1_should_bucket_low() {
      Finding finding = findingWith(ContractOutputType.CVE, "2.1");
      assertEquals(FindingSeverityBucket.LOW, service.normalize(finding));
    }
  }

  @Nested
  @DisplayName("Free-text CVE/OCSF labels")
  class TextLabels {

    @Test
    @DisplayName("Should bucket 'Critical' label as CRITICAL")
    void given_label_critical_should_bucket_critical() {
      Finding finding = findingWith(ContractOutputType.CVE, "Critical");
      assertEquals(FindingSeverityBucket.CRITICAL, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket 'high' label (lowercase) as HIGH")
    void given_label_high_lowercase_should_bucket_high() {
      Finding finding = findingWith(ContractOutputType.CVE, "high");
      assertEquals(FindingSeverityBucket.HIGH, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket OCSF 'Informational' label as UNKNOWN")
    void given_ocsf_informational_should_bucket_unknown() {
      Finding finding = findingWith(ContractOutputType.OCSF, "Informational");
      assertEquals(FindingSeverityBucket.UNKNOWN, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket unrecognized free text as UNKNOWN, never silently MEDIUM")
    void given_unrecognized_text_should_bucket_unknown() {
      Finding finding = findingWith(ContractOutputType.CVE, "not-a-real-severity");
      assertEquals(FindingSeverityBucket.UNKNOWN, service.normalize(finding));
    }
  }

  @Nested
  @DisplayName("finding_type fallback when severity is absent (majority of injectors today)")
  class TypeFallback {

    @Test
    @DisplayName("Should bucket Credentials (Netexec) as HIGH by default")
    void given_credentials_no_severity_should_bucket_high() {
      Finding finding = findingWith(ContractOutputType.Credentials, null);
      assertEquals(FindingSeverityBucket.HIGH, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket Vulnerability (AI Red Team) as HIGH by default")
    void given_vulnerability_no_severity_should_bucket_high() {
      Finding finding = findingWith(ContractOutputType.Vulnerability, "");
      assertEquals(FindingSeverityBucket.HIGH, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket Sid (Netexec) as MEDIUM by default")
    void given_sid_no_severity_should_bucket_medium() {
      Finding finding = findingWith(ContractOutputType.Sid, null);
      assertEquals(FindingSeverityBucket.MEDIUM, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket PortsScan (Nmap) as LOW by default")
    void given_portscan_no_severity_should_bucket_low() {
      Finding finding = findingWith(ContractOutputType.PortsScan, null);
      assertEquals(FindingSeverityBucket.LOW, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket Text (AWS, unstructured output) as UNKNOWN, never guessed")
    void given_text_no_severity_should_bucket_unknown() {
      Finding finding = findingWith(ContractOutputType.Text, null);
      assertEquals(FindingSeverityBucket.UNKNOWN, service.normalize(finding));
    }

    @Test
    @DisplayName("Should bucket a finding type with no fallback entry as UNKNOWN")
    void given_type_without_fallback_entry_should_bucket_unknown() {
      Finding finding = findingWith(ContractOutputType.Email, null);
      assertEquals(FindingSeverityBucket.UNKNOWN, service.normalize(finding));
    }
  }
}
