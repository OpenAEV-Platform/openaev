package io.openaev.finding.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ContractOutputType;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Finding value normalizer")
class FindingValueNormalizerTest {

  private static final LocationIdentity ASSET_A = new AssetLocation("asset-a");
  private static final LocationIdentity UNKNOWN = UnknownLocation.INSTANCE;

  private static String hash(ContractOutputType type, String value) {
    return FindingValueNormalizer.hash(type, value, ASSET_A);
  }

  private static String hash(ContractOutputType type, String value, LocationIdentity location) {
    return FindingValueNormalizer.hash(type, value, location);
  }

  private static String normalized(ContractOutputType type, String value) {
    return FindingValueNormalizer.normalize(type, value, ASSET_A);
  }

  private static String normalized(
      ContractOutputType type, String value, LocationIdentity location) {
    return FindingValueNormalizer.normalize(type, value, location);
  }

  @Nested
  @DisplayName("When hashing")
  class WhenHashing {

    @Test
    @DisplayName("given a value, should hash its normalized form as SHA-256 lower-case hex")
    void given_aValue_should_hashItsNormalizedFormAsSha256LowerCaseHex() {
      // -- ACT --
      String identity = hash(ContractOutputType.CVE, "cve-2024-1234");

      // -- ASSERT --
      assertThat(normalized(ContractOutputType.CVE, "cve-2024-1234")).isEqualTo("CVE-2024-1234");
      assertThat(identity)
          .hasSize(64)
          .matches("[0-9a-f]+")
          .isEqualTo(hash(ContractOutputType.CVE, "CVE-2024-1234"));
    }

    @Test
    @DisplayName("given surrounding whitespace, should ignore it")
    void given_surroundingWhitespace_should_ignoreIt() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Text, "  some output \n"))
          .isEqualTo(hash(ContractOutputType.Text, "some output"));
    }

    @Test
    @DisplayName("given a sensitive value, should keep it out of toString")
    void given_aSensitiveValue_should_keepItOutOfToString() {
      // -- ARRANGE --
      DetectedFinding detection =
          new DetectedFinding(
              ContractOutputType.Credentials,
              "jdoe:Sup3rS3cret",
              "credentials",
              "jdoe",
              ASSET_A,
              Set.of());

      // -- ACT & ASSERT --
      assertThat(detection.toString()).doesNotContain("Sup3rS3cret");
    }

    @Test
    @DisplayName("given two output keys, should resolve to the same identity")
    void given_twoOutputKeys_should_resolveToTheSameIdentity() {
      // -- ARRANGE --
      DetectedFinding fromCve =
          new DetectedFinding(
              ContractOutputType.CVE, "CVE-2024-1234", "cve", null, ASSET_A, Set.of());
      DetectedFinding fromScan =
          new DetectedFinding(
              ContractOutputType.CVE, "CVE-2024-1234", "scan_cves", null, ASSET_A, Set.of());

      // -- ACT & ASSERT --
      assertThat(fromCve.valueHash()).isEqualTo(fromScan.valueHash());
    }
  }

  @Nested
  @DisplayName("When the platform ignores case")
  class WhenThePlatformIgnoresCase {

    @Test
    @DisplayName("given a CVE or a SID in any case, should resolve to the same identity")
    void given_aCveOrASidInAnyCase_should_resolveToTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.CVE, "cve-2024-1234"))
          .isEqualTo(hash(ContractOutputType.CVE, "CVE-2024-1234"));
      assertThat(hash(ContractOutputType.Sid, "s-1-5-21-1004"))
          .isEqualTo(hash(ContractOutputType.Sid, "S-1-5-21-1004"));
    }

    @Test
    @DisplayName("given an Active Directory name in any case, should resolve to the same identity")
    void given_anActiveDirectoryNameInAnyCase_should_resolveToTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Username, "NORTH\\JDoe"))
          .isEqualTo(hash(ContractOutputType.Username, "north\\jdoe"));
      assertThat(hash(ContractOutputType.KerberoastableAccount, "SVC_SQL"))
          .isEqualTo(hash(ContractOutputType.KerberoastableAccount, "svc_sql"));
      assertThat(hash(ContractOutputType.AsreproastableAccount, "Brandon.Stark"))
          .isEqualTo(hash(ContractOutputType.AsreproastableAccount, "brandon.stark"));
      assertThat(hash(ContractOutputType.Computer, "WINTERFELL$"))
          .isEqualTo(hash(ContractOutputType.Computer, "winterfell$"));
    }

    @Test
    @DisplayName("given an email in any case, should resolve to the same identity")
    void given_anEmailInAnyCase_should_resolveToTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Email, "John.Doe@Example.COM"))
          .isEqualTo(hash(ContractOutputType.Email, "john.doe@example.com"));
    }
  }

  @Nested
  @DisplayName("When case carries meaning")
  class WhenCaseCarriesMeaning {

    @Test
    @DisplayName("given credentials differing by case, should resolve to different identities")
    void given_credentialsDifferingByCase_should_resolveToDifferentIdentities() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Credentials, "jdoe:Secret"))
          .isNotEqualTo(hash(ContractOutputType.Credentials, "jdoe:secret"));
      assertThat(hash(ContractOutputType.Credentials, "Root:secret"))
          .isNotEqualTo(hash(ContractOutputType.Credentials, "root:secret"));
    }

    @Test
    @DisplayName(
        "given credentials differing by password whitespace, should resolve to different identities")
    void given_credentialsDifferingByPasswordWhitespace_should_resolveToDifferentIdentities() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Credentials, "jdoe:secret "))
          .isNotEqualTo(hash(ContractOutputType.Credentials, "jdoe:secret"));
    }

    @Test
    @DisplayName("given a bare username differing by case, should resolve to different identities")
    void given_aBareUsernameDifferingByCase_should_resolveToDifferentIdentities() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Username, "Root"))
          .isNotEqualTo(hash(ContractOutputType.Username, "root"));
      assertThat(hash(ContractOutputType.AdminUsername, "Root"))
          .isNotEqualTo(hash(ContractOutputType.AdminUsername, "root"));
    }

    @Test
    @DisplayName("given file paths differing by case, should resolve to different identities")
    void given_filePathsDifferingByCase_should_resolveToDifferentIdentities() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.File, "srv:/etc/Passwd"))
          .isNotEqualTo(hash(ContractOutputType.File, "srv:/etc/passwd"));
    }
  }

  @Nested
  @DisplayName("When the value carries a changing state")
  class WhenTheValueCarriesAChangingState {

    @Test
    @DisplayName("given a CVE with text around its id, should keep only the id")
    void given_aCveWithTextAroundItsId_should_keepOnlyTheId() {
      // -- ACT & ASSERT --
      assertThat(normalized(ContractOutputType.CVE, "cve-2026-0234 (critical)"))
          .isEqualTo("CVE-2026-0234");
      assertThat(hash(ContractOutputType.CVE, "CVE-2026-0234 (CVSS 9.8)"))
          .isEqualTo(hash(ContractOutputType.CVE, "CVE-2026-0234"));
      assertThat(normalized(ContractOutputType.CVE, "Unknown CVE")).isEqualTo("UNKNOWN CVE");
    }

    @Test
    @DisplayName("given a group whose member count changed, should stay the same identity")
    void given_aGroupWhoseMemberCountChanged_should_stayTheSameIdentity() {
      // -- ACT --
      String monday = hash(ContractOutputType.Group, "Domain Admins (12 members)");
      String tuesday = hash(ContractOutputType.Group, "Domain Admins (13 members)");

      // -- ASSERT --
      assertThat(monday).isEqualTo(tuesday);
      assertThat(normalized(ContractOutputType.Group, "Domain Admins (12 members)"))
          .isEqualTo("domain admins");
    }

    @Test
    @DisplayName("given a vulnerability whose status changed, should stay the same identity")
    void given_aVulnerabilityWhoseStatusChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Vulnerability, "ZeroLogon [VULNERABLE]"))
          .isEqualTo(hash(ContractOutputType.Vulnerability, "ZeroLogon [PATCHED]"));
    }

    @Test
    @DisplayName("given a password policy setting whose value changed, should stay the same")
    void given_aPasswordPolicySettingWhoseValueChanged_should_stayTheSame() {
      // -- ACT --
      String before = hash(ContractOutputType.PasswordPolicy, "MinimumPasswordLength: 7");
      String after = hash(ContractOutputType.PasswordPolicy, "MinimumPasswordLength: 8");

      // -- ASSERT --
      assertThat(before).isEqualTo(after);
      assertThat(before)
          .isNotEqualTo(hash(ContractOutputType.PasswordPolicy, "LockoutThreshold: 7"));
    }

    @Test
    @DisplayName("given an account whose status changed, should stay the same identity")
    void given_anAccountWhoseStatusChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.AccountWithPasswordNotRequired, "guest (enabled)"))
          .isEqualTo(hash(ContractOutputType.AccountWithPasswordNotRequired, "guest (disabled)"));
    }

    @Test
    @DisplayName("given a port whose detected service changed, should stay the same identity")
    void given_aPortWhoseDetectedServiceChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.PortsScan, "10.0.0.5:445 (microsoft-ds)"))
          .isEqualTo(hash(ContractOutputType.PortsScan, "10.0.0.5:445 (smb)"));
    }

    @Test
    @DisplayName("given a share whose permissions changed, should stay the same identity")
    void given_aShareWhosePermissionsChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Share, "\\\\dc01\\C$ (READ)"))
          .isEqualTo(hash(ContractOutputType.Share, "\\\\dc01\\C$ (READ,WRITE)"));
    }

    @Test
    @DisplayName("given a delegation, should keep its type and target in the identity")
    void given_aDelegation_should_keepItsTypeAndTargetInTheIdentity() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Delegation, "svc_web [Constrained] -> cifs/dc01"))
          .isNotEqualTo(hash(ContractOutputType.Delegation, "svc_web [Unconstrained]"));
    }
  }

  @Nested
  @DisplayName("When the value names its host")
  class WhenTheValueNamesItsHost {

    private static final LocationIdentity ASSET_B = new AssetLocation("asset-b");

    @Test
    @DisplayName("given the same port on two assets, should share the identity hash")
    void given_theSamePortOnTwoAssets_should_shareTheIdentityHash() {
      // -- ACT --
      String onA = hash(ContractOutputType.PortsScan, "10.0.0.5:445", ASSET_A);
      String onB = hash(ContractOutputType.PortsScan, "10.0.0.6:445", ASSET_B);

      // -- ASSERT --
      assertThat(onA).isEqualTo(onB);
      assertThat(normalized(ContractOutputType.PortsScan, "10.0.0.5:445", ASSET_A))
          .isEqualTo("445");
    }

    @Test
    @DisplayName("given a service name holding parentheses, should still split the port off")
    void given_aServiceNameHoldingParentheses_should_stillSplitThePortOff() {
      // -- ACT & ASSERT --
      String shodanPort = "10.0.0.5:443 (Apache httpd (Ubuntu))";
      assertThat(normalized(ContractOutputType.PortsScan, shodanPort, ASSET_A)).isEqualTo("443");
      assertThat(normalized(ContractOutputType.PortsScan, shodanPort, UNKNOWN))
          .isEqualTo("10.0.0.5:443");
    }

    @Test
    @DisplayName("given the same port on two hosts without asset, should keep them apart")
    void given_theSamePortOnTwoHostsWithoutAsset_should_keepThemApart() {
      // -- ACT --
      String first = hash(ContractOutputType.PortsScan, "10.0.0.5:445", UNKNOWN);
      String second = hash(ContractOutputType.PortsScan, "10.0.0.6:445", UNKNOWN);

      // -- ASSERT --
      assertThat(first).isNotEqualTo(second);
      assertThat(normalized(ContractOutputType.PortsScan, "10.0.0.5:445", UNKNOWN))
          .isEqualTo("10.0.0.5:445");
    }

    @Test
    @DisplayName("given an IPv6 host, should still split the port off")
    void given_anIpv6Host_should_stillSplitThePortOff() {
      // -- ACT & ASSERT --
      assertThat(normalized(ContractOutputType.PortsScan, "fe80::1:22 (ssh)")).isEqualTo("22");
      assertThat(normalized(ContractOutputType.PortsScan, "FE80::1:22 (ssh)", UNKNOWN))
          .isEqualTo("fe80::1:22");
    }

    @Test
    @DisplayName("given the same share on two assets, should share the identity hash")
    void given_theSameShareOnTwoAssets_should_shareTheIdentityHash() {
      // -- ACT & ASSERT --
      assertThat(hash(ContractOutputType.Share, "\\\\dc01\\SYSVOL (READ)", ASSET_A))
          .isEqualTo(hash(ContractOutputType.Share, "\\\\dc02\\sysvol (READ)", ASSET_B));
      assertThat(hash(ContractOutputType.Share, "\\\\dc01\\SYSVOL (READ)", UNKNOWN))
          .isNotEqualTo(hash(ContractOutputType.Share, "\\\\dc02\\SYSVOL (READ)", UNKNOWN));
    }

    @Test
    @DisplayName("given the same SMB file on two assets, should match the file seen without host")
    void given_theSameSmbFileOnTwoAssets_should_matchTheFileSeenWithoutHost() {
      // -- ARRANGE --
      String relative = "SYSVOL/north.local/scripts/secret.ps1";

      // -- ACT --
      String onA =
          hash(
              ContractOutputType.File,
              "\\\\dc01\\SYSVOL\\north.local\\scripts\\secret.ps1",
              ASSET_A);
      String onB =
          hash(
              ContractOutputType.File,
              "\\\\dc02\\SYSVOL\\north.local\\scripts\\secret.ps1",
              ASSET_B);

      // -- ASSERT --
      assertThat(onA).isEqualTo(onB);
      assertThat(
              normalized(
                  ContractOutputType.File,
                  "\\\\dc01\\SYSVOL\\north.local\\scripts\\secret.ps1",
                  ASSET_A))
          .isEqualTo(relative);
      assertThat(hash(ContractOutputType.File, relative, ASSET_B)).isEqualTo(onA);
    }

    @Test
    @DisplayName("given the same local file on two assets, should share the identity hash")
    void given_theSameLocalFileOnTwoAssets_should_shareTheIdentityHash() {
      // -- ACT --
      String onA = hash(ContractOutputType.File, "srv1:/etc/shadow", ASSET_A);
      String onB = hash(ContractOutputType.File, "SRV2:/etc/shadow", ASSET_B);

      // -- ASSERT --
      assertThat(onA).isEqualTo(onB);
      assertThat(normalized(ContractOutputType.File, "srv1:/etc/shadow", ASSET_A))
          .isEqualTo("/etc/shadow");
      assertThat(normalized(ContractOutputType.File, "SRV1:/etc/shadow", UNKNOWN))
          .isEqualTo("srv1:/etc/shadow");
    }
  }
}
