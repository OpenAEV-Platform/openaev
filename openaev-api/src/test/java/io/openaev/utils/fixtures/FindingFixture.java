package io.openaev.utils.fixtures;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import java.util.UUID;

public class FindingFixture {

  public static final String TEXT_FIELD = "text_field";
  public static final String IPV6_FIELD = "ipv6_field";
  public static final String CREDENTIALS_FIELD = "credentials_field";
  public static final String PASSWORD_POLICY_FIELD = "password_policy_field";
  public static final String ACCOUNT_FIELD = "account_field";
  public static final String CREDENTIALS_USERNAME = "admin";
  public static final String CREDENTIALS_PASSWORD = "motdepasse";

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
    finding.setName("vulnerability");
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

  /**
   * Credentials whose password differs from the username, unlike {@code admin:admin}: a search on
   * the password then cannot match the readable username instead, so it tells whether the secret
   * itself is searchable.
   */
  public static Finding createFindingCredentialsWithDistinctPassword() {
    Finding finding = createDefaultFindingCredentials();
    finding.setValue(CREDENTIALS_USERNAME + ":" + CREDENTIALS_PASSWORD);
    return finding;
  }

  public static Finding createDefaultPasswordPolicyFinding() {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.PasswordPolicy);
    finding.setName("Password policy");
    finding.setField(PASSWORD_POLICY_FIELD);
    finding.setValue("Minimum password length: 8");
    return finding;
  }

  public static Finding createDefaultKerberoastableAccountFinding() {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.KerberoastableAccount);
    finding.setName("Kerberoastable account");
    finding.setField(ACCOUNT_FIELD);
    finding.setValue("svc_sqlserver");
    return finding;
  }

  public static Finding createDefaultAsreproastableAccountFinding() {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.AsreproastableAccount);
    finding.setName("AS-REP roastable account");
    finding.setField(ACCOUNT_FIELD);
    finding.setValue("jdoe_nopreauth");
    return finding;
  }
}
