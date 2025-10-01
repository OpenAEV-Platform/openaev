package io.openaev.utils.fixtures;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import java.util.UUID;

public class FindingFixture {

  public static final String TEXT_FIELD = "text_field";
  public static final String IPV6_FIELD = "ipv6_field";
  public static final String CREDENTIALS_FIELD = "credentials_field";

  public static Finding createDefaultTextFinding() {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.Text);
    finding.setName("Text");
    finding.setField(TEXT_FIELD);
    finding.setValue("text_value");
    finding.setLabels(new String[] {"reconnaissance phase"});
    return finding;
  }

  public static Finding createDefaultTextFindingWithRandomValue() {
    Finding finding = createDefaultTextFinding();
    finding.setValue(UUID.randomUUID().toString());
    return finding;
  }

  public static Finding createDefaultCveFindingWithRandomTitle() {
    String cveName =
        "CVE-%d-%d"
            .formatted(Math.round(Math.random() * 1000), Math.round(Math.random() * 1000000));
    Finding finding = new Finding();
    finding.setType(ContractOutputType.CVE);
    finding.setName("cve");
    finding.setField(TEXT_FIELD);
    finding.setValue(cveName);
    finding.setLabels(new String[] {"reconnaissance phase"});
    return finding;
  }

  public static Finding createDefaultIPV6Finding() {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.IPv6);
    finding.setName("Ipv6");
    finding.setField(IPV6_FIELD);
    finding.setValue("2001:0000:130F:0000:0000:09C0:876A:130B");
    return finding;
  }

  public static Finding createDefaultFindingCredentials() {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.Credentials);
    finding.setName("Credentials");
    finding.setField(CREDENTIALS_FIELD);
    finding.setValue("admin:admin");
    return finding;
  }
}
