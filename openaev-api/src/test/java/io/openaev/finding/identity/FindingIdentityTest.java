package io.openaev.finding.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ContractOutputType;
import io.openaev.finding.identity.FindingLocation.AssetLocation;
import io.openaev.finding.identity.FindingLocation.UnknownLocation;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Finding identity")
class FindingIdentityTest {

  private static final FindingLocation ASSET_A = new AssetLocation("asset-a");
  private static final FindingLocation UNKNOWN = UnknownLocation.INSTANCE;

  private static FindingIdentity identity(ContractOutputType type, String value) {
    return FindingIdentity.of(type, value, ASSET_A);
  }

  private static FindingIdentity identity(
      ContractOutputType type, String value, FindingLocation location) {
    return FindingIdentity.of(type, value, location);
  }

  @Nested
  @DisplayName("When hashing")
  class WhenHashing {

    @Test
    @DisplayName("given a value, should hash its normalized form as SHA-256 lower-case hex")
    void given_aValue_should_hashItsNormalizedFormAsSha256LowerCaseHex() {
      // -- ACT --
      FindingIdentity identity = identity(ContractOutputType.CVE, "cve-2024-1234");

      // -- ASSERT --
      assertThat(identity.normalizedValue()).isEqualTo("CVE-2024-1234");
      assertThat(identity.hash())
          .hasSize(64)
          .matches("[0-9a-f]+")
          .isEqualTo(identity(ContractOutputType.CVE, "CVE-2024-1234").hash());
    }

    @Test
    @DisplayName("given surrounding whitespace, should ignore it")
    void given_surroundingWhitespace_should_ignoreIt() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Text, "  some output \n"))
          .isEqualTo(identity(ContractOutputType.Text, "some output"));
    }

    @Test
    @DisplayName("given a sensitive value, should keep it out of toString")
    void given_aSensitiveValue_should_keepItOutOfToString() {
      // -- ARRANGE --
      FindingDetection detection =
          new FindingDetection(
              ContractOutputType.Credentials, "jdoe:Sup3rS3cret", "jdoe", ASSET_A, Set.of());

      // -- ACT & ASSERT --
      assertThat(detection.toString()).doesNotContain("Sup3rS3cret");
      assertThat(detection.identity().toString()).doesNotContain("Sup3rS3cret");
    }
  }

  @Nested
  @DisplayName("When the platform ignores case")
  class WhenThePlatformIgnoresCase {

    @Test
    @DisplayName("given a CVE or a SID in any case, should resolve to the same identity")
    void given_aCveOrASidInAnyCase_should_resolveToTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.CVE, "cve-2024-1234"))
          .isEqualTo(identity(ContractOutputType.CVE, "CVE-2024-1234"));
      assertThat(identity(ContractOutputType.Sid, "s-1-5-21-1004"))
          .isEqualTo(identity(ContractOutputType.Sid, "S-1-5-21-1004"));
    }

    @Test
    @DisplayName("given an Active Directory name in any case, should resolve to the same identity")
    void given_anActiveDirectoryNameInAnyCase_should_resolveToTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Username, "NORTH\\JDoe"))
          .isEqualTo(identity(ContractOutputType.Username, "north\\jdoe"));
      assertThat(identity(ContractOutputType.AdminUsername, "Administrator"))
          .isEqualTo(identity(ContractOutputType.AdminUsername, "administrator"));
      assertThat(identity(ContractOutputType.KerberoastableAccount, "SVC_SQL"))
          .isEqualTo(identity(ContractOutputType.KerberoastableAccount, "svc_sql"));
      assertThat(identity(ContractOutputType.AsreproastableAccount, "Brandon.Stark"))
          .isEqualTo(identity(ContractOutputType.AsreproastableAccount, "brandon.stark"));
      assertThat(identity(ContractOutputType.Computer, "WINTERFELL$"))
          .isEqualTo(identity(ContractOutputType.Computer, "winterfell$"));
    }

    @Test
    @DisplayName("given an email in any case, should resolve to the same identity")
    void given_anEmailInAnyCase_should_resolveToTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Email, "John.Doe@Example.COM"))
          .isEqualTo(identity(ContractOutputType.Email, "john.doe@example.com"));
    }
  }

  @Nested
  @DisplayName("When case carries meaning")
  class WhenCaseCarriesMeaning {

    @Test
    @DisplayName("given credentials differing by case, should resolve to different identities")
    void given_credentialsDifferingByCase_should_resolveToDifferentIdentities() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Credentials, "jdoe:Secret"))
          .isNotEqualTo(identity(ContractOutputType.Credentials, "jdoe:secret"));
      assertThat(identity(ContractOutputType.Credentials, "Root:secret"))
          .isNotEqualTo(identity(ContractOutputType.Credentials, "root:secret"));
    }

    @Test
    @DisplayName("given file paths differing by case, should resolve to different identities")
    void given_filePathsDifferingByCase_should_resolveToDifferentIdentities() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.File, "srv:/etc/Passwd"))
          .isNotEqualTo(identity(ContractOutputType.File, "srv:/etc/passwd"));
    }
  }

  @Nested
  @DisplayName("When the value carries a changing state")
  class WhenTheValueCarriesAChangingState {

    @Test
    @DisplayName("given a group whose member count changed, should stay the same identity")
    void given_aGroupWhoseMemberCountChanged_should_stayTheSameIdentity() {
      // -- ACT --
      FindingIdentity monday = identity(ContractOutputType.Group, "Domain Admins (12 members)");
      FindingIdentity tuesday = identity(ContractOutputType.Group, "Domain Admins (13 members)");

      // -- ASSERT --
      assertThat(monday).isEqualTo(tuesday);
      assertThat(monday.normalizedValue()).isEqualTo("domain admins");
    }

    @Test
    @DisplayName("given a vulnerability whose status changed, should stay the same identity")
    void given_aVulnerabilityWhoseStatusChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Vulnerability, "ZeroLogon [VULNERABLE]"))
          .isEqualTo(identity(ContractOutputType.Vulnerability, "ZeroLogon [PATCHED]"));
    }

    @Test
    @DisplayName("given a password policy setting whose value changed, should stay the same")
    void given_aPasswordPolicySettingWhoseValueChanged_should_stayTheSame() {
      // -- ACT --
      FindingIdentity before = identity(ContractOutputType.PasswordPolicy, "MinimumPasswordLength: 7");
      FindingIdentity after = identity(ContractOutputType.PasswordPolicy, "MinimumPasswordLength: 8");

      // -- ASSERT --
      assertThat(before).isEqualTo(after);
      assertThat(before)
          .isNotEqualTo(identity(ContractOutputType.PasswordPolicy, "LockoutThreshold: 7"));
    }

    @Test
    @DisplayName("given an account whose status changed, should stay the same identity")
    void given_anAccountWhoseStatusChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.AccountWithPasswordNotRequired, "guest (enabled)"))
          .isEqualTo(
              identity(ContractOutputType.AccountWithPasswordNotRequired, "guest (disabled)"));
    }

    @Test
    @DisplayName("given a port whose detected service changed, should stay the same identity")
    void given_aPortWhoseDetectedServiceChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.PortsScan, "10.0.0.5:445 (microsoft-ds)"))
          .isEqualTo(identity(ContractOutputType.PortsScan, "10.0.0.5:445 (smb)"));
    }

    @Test
    @DisplayName("given a share whose permissions changed, should stay the same identity")
    void given_aShareWhosePermissionsChanged_should_stayTheSameIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Share, "\\\\dc01\\C$ (READ)"))
          .isEqualTo(identity(ContractOutputType.Share, "\\\\dc01\\C$ (READ,WRITE)"));
    }

    @Test
    @DisplayName("given a delegation, should keep its type and target in the identity")
    void given_aDelegation_should_keepItsTypeAndTargetInTheIdentity() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Delegation, "svc_web [Constrained] -> cifs/dc01"))
          .isNotEqualTo(identity(ContractOutputType.Delegation, "svc_web [Unconstrained]"));
    }
  }

  @Nested
  @DisplayName("When the value names its host")
  class WhenTheValueNamesItsHost {

    private static final FindingLocation ASSET_B = new AssetLocation("asset-b");

    @Test
    @DisplayName("given the same port on two assets, should share the identity hash")
    void given_theSamePortOnTwoAssets_should_shareTheIdentityHash() {
      // -- ACT --
      FindingIdentity onA = identity(ContractOutputType.PortsScan, "10.0.0.5:445", ASSET_A);
      FindingIdentity onB = identity(ContractOutputType.PortsScan, "10.0.0.6:445", ASSET_B);

      // -- ASSERT --
      assertThat(onA.hash()).isEqualTo(onB.hash());
      assertThat(onA.normalizedValue()).isEqualTo("445");
    }

    @Test
    @DisplayName("given the same port on two hosts without asset, should keep them apart")
    void given_theSamePortOnTwoHostsWithoutAsset_should_keepThemApart() {
      // -- ACT --
      FindingIdentity first = identity(ContractOutputType.PortsScan, "10.0.0.5:445", UNKNOWN);
      FindingIdentity second = identity(ContractOutputType.PortsScan, "10.0.0.6:445", UNKNOWN);

      // -- ASSERT --
      assertThat(first).isNotEqualTo(second);
      assertThat(first.normalizedValue()).isEqualTo("10.0.0.5:445");
    }

    @Test
    @DisplayName("given an IPv6 host, should still split the port off")
    void given_anIpv6Host_should_stillSplitThePortOff() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.PortsScan, "fe80::1:22 (ssh)").normalizedValue())
          .isEqualTo("22");
      assertThat(
              identity(ContractOutputType.PortsScan, "FE80::1:22 (ssh)", UNKNOWN)
                  .normalizedValue())
          .isEqualTo("fe80::1:22");
    }

    @Test
    @DisplayName("given the same share on two assets, should share the identity hash")
    void given_theSameShareOnTwoAssets_should_shareTheIdentityHash() {
      // -- ACT & ASSERT --
      assertThat(identity(ContractOutputType.Share, "\\\\dc01\\SYSVOL (READ)", ASSET_A).hash())
          .isEqualTo(identity(ContractOutputType.Share, "\\\\dc02\\sysvol (READ)", ASSET_B).hash());
      assertThat(identity(ContractOutputType.Share, "\\\\dc01\\SYSVOL (READ)", UNKNOWN))
          .isNotEqualTo(identity(ContractOutputType.Share, "\\\\dc02\\SYSVOL (READ)", UNKNOWN));
    }

    @Test
    @DisplayName("given the same SMB file on two assets, should match the file seen without host")
    void given_theSameSmbFileOnTwoAssets_should_matchTheFileSeenWithoutHost() {
      // -- ARRANGE --
      String relative = "SYSVOL/north.local/scripts/secret.ps1";

      // -- ACT --
      FindingIdentity onA =
          identity(
              ContractOutputType.File, "\\\\dc01\\SYSVOL\\north.local\\scripts\\secret.ps1", ASSET_A);
      FindingIdentity onB =
          identity(
              ContractOutputType.File, "\\\\dc02\\SYSVOL\\north.local\\scripts\\secret.ps1", ASSET_B);

      // -- ASSERT --
      assertThat(onA.hash()).isEqualTo(onB.hash());
      assertThat(onA.normalizedValue()).isEqualTo(relative);
      assertThat(identity(ContractOutputType.File, relative, ASSET_B).hash()).isEqualTo(onA.hash());
    }

    @Test
    @DisplayName("given the same local file on two assets, should share the identity hash")
    void given_theSameLocalFileOnTwoAssets_should_shareTheIdentityHash() {
      // -- ACT --
      FindingIdentity onA = identity(ContractOutputType.File, "srv1:/etc/shadow", ASSET_A);
      FindingIdentity onB = identity(ContractOutputType.File, "SRV2:/etc/shadow", ASSET_B);

      // -- ASSERT --
      assertThat(onA.hash()).isEqualTo(onB.hash());
      assertThat(onA.normalizedValue()).isEqualTo("/etc/shadow");
      assertThat(identity(ContractOutputType.File, "SRV1:/etc/shadow", UNKNOWN).normalizedValue())
          .isEqualTo("srv1:/etc/shadow");
    }
  }
}
