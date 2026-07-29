package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the consumed-key ↔ produced-finding matcher (mirror of the front matcher). */
class AttackPathKeyMatcherTest {

  private static AttackPathFindingRow finding(String type, String value) {
    return new AttackPathFindingRow("f-id", type, value, null, null, "ep", "exec");
  }

  private static ConsumedFindingKeyDTO key(String keyType, String operator, String value) {
    return new ConsumedFindingKeyDTO(keyType, operator, value, null);
  }

  @Test
  @DisplayName(
      "the key type is reconciled to the finding type (share_name -> share, password -> credentials)")
  void reconciles_key_type_to_finding_type() {
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", "\\\\host\\NETLOGON"), key("share_name", "IS_NOT_NULL", null)))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("credentials", "admin:secret"), key("password", "IS_NOT_NULL", null)))
        .isTrue();
    // A key type with no mapping matches its own finding type 1:1 (identity).
    assertThat(
            AttackPathKeyMatcher.matches(finding("port", "445"), key("port", "IS_NOT_NULL", null)))
        .isTrue();
  }

  @Test
  @DisplayName("a type mismatch never matches")
  void type_mismatch_never_matches() {
    // share_name reconciles to share, which is not the port finding's type.
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("port", "445"), key("share_name", "IS_NOT_NULL", null)))
        .isFalse();
  }

  @Test
  @DisplayName("IS_NOT_NULL matches on presence of a non-blank value")
  void is_not_null_is_presence() {
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", "\\\\host\\NETLOGON"), key("share_name", "IS_NOT_NULL", null)))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", ""), key("share_name", "IS_NOT_NULL", null)))
        .isFalse();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", null), key("share_name", "IS_NOT_NULL", null)))
        .isFalse();
  }

  @Test
  @DisplayName("EQ matches the exact value; a null key value never matches")
  void eq_matches_exact_value() {
    assertThat(AttackPathKeyMatcher.matches(finding("port", "445"), key("port", "EQ", "445")))
        .isTrue();
    assertThat(AttackPathKeyMatcher.matches(finding("port", "443"), key("port", "EQ", "445")))
        .isFalse();
    assertThat(AttackPathKeyMatcher.matches(finding("port", "445"), key("port", "EQ", null)))
        .isFalse();
  }

  @Test
  @DisplayName("IN matches a comma-separated member; a single token falls back to substring")
  void in_matches_member_or_substring() {
    assertThat(
            AttackPathKeyMatcher.matches(finding("port", "445"), key("port", "IN", "139,445,3389")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(finding("port", "22"), key("port", "IN", "139,445,3389")))
        .isFalse();
    // Single token: substring containment (mirrors the front).
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", "\\\\host\\NETLOGON"), key("share_name", "IN", "NETLOGON")))
        .isTrue();
  }

  @Test
  @DisplayName("a null key type reconciles to null and never matches (no NPE from Map.of)")
  void null_key_type_is_safe() {
    assertThat(AttackPathKeyMatcher.reconciledType(null)).isNull();
    assertThat(AttackPathKeyMatcher.matches(finding("share", "x"), key(null, "IS_NOT_NULL", null)))
        .isFalse();
  }

  @Test
  @DisplayName("an unsupported operator matches nothing")
  void unsupported_operator_matches_nothing() {
    assertThat(AttackPathKeyMatcher.matches(finding("port", "445"), key("port", "GT", "400")))
        .isFalse();
    assertThat(AttackPathKeyMatcher.matches(finding("port", "445"), key("port", null, "445")))
        .isFalse();
  }

  @Test
  @DisplayName("a port key reaches the port sub-field of a portscan finding (host:port (service))")
  void matches_portscan_port_subfield() {
    // A portscan value is the formatted "host:port (service)"
    // (PortScanOutputProcessor.toFindingValue),
    // one finding per host+port. A `port` key must reach the port sub-field, not compare the whole
    // value.
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "10.0.3.11:445 (SMB)"), key("port", "EQ", "445")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "10.0.3.11:445 (SMB)"), key("port", "EQ", "22")))
        .isFalse();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "10.0.3.11:445 (SMB)"), key("port", "IN", "139,445,3389")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "10.0.3.11:445 (SMB)"), key("port", "IS_NOT_NULL", null)))
        .isTrue();
    // Pitfall: an IPv6 host contains ':'; the port is the numeric token before the optional "
    // (service)",
    // not split(':')[1].
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "2001:db8::1:445 (SMB)"), key("port", "EQ", "445")))
        .isTrue();
    // Pitfall: no " (service)" segment.
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "10.0.3.11:445"), key("port", "EQ", "445")))
        .isTrue();
  }

  @Test
  @DisplayName("username/password keys reach the sub-fields of a credentials finding (user:pass)")
  void matches_credentials_subfields() {
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("credentials", "admin:secret"), key("username", "EQ", "admin")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("credentials", "admin:secret"), key("password", "EQ", "secret")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("credentials", "admin:secret"), key("password", "EQ", "admin")))
        .isFalse();
    // A password containing ':' splits on the FIRST separator (the username has none).
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("credentials", "svc:p:ss"), key("password", "EQ", "p:ss")))
        .isTrue();
  }

  @Test
  @DisplayName("a share_name key reaches the share_name sub-field of a native share finding")
  void matches_share_name_subfield() {
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", "\\\\host\\NETLOGON (RW)"), key("share_name", "EQ", "NETLOGON")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("share", "\\\\host\\NETLOGON"), key("share_name", "EQ", "SYSVOL")))
        .isFalse();
  }

  @Test
  @DisplayName("a file_name key reaches the basename of a native file finding")
  void matches_file_name_subfield() {
    // A file value is the full location (FileOutputProcessor.toFindingValue); a file_name key must
    // reach the basename, not compare the whole path.
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("file", "\\\\host\\SYSVOL\\scripts\\secret.ps1"),
                key("file_name", "EQ", "secret.ps1")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("file", "host:/home/user/config.ini"),
                key("file_name", "EQ", "config.ini")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("file", "\\\\host\\SYSVOL\\scripts\\secret.ps1"),
                key("file_name", "EQ", "other.ps1")))
        .isFalse();
    // A share_name key must NOT match a file finding (a file is not a share): no spurious edge.
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("file", "\\\\host\\SYSVOL\\scripts\\secret.ps1"),
                key("share_name", "EQ", "SYSVOL")))
        .isFalse();
  }

  @Test
  @DisplayName("username/domain keys reach the sub-fields of a username finding (domain\\user)")
  void matches_username_finding_subfields() {
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("username", "CORP\\jdoe"), key("username", "EQ", "jdoe")))
        .isTrue();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("username", "CORP\\jdoe"), key("domain", "EQ", "CORP")))
        .isTrue();
  }

  @Test
  @DisplayName("an unparsable complex value never matches and never throws")
  void unparsable_complex_value_never_matches() {
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("portscan", "host-no-colon"), key("port", "EQ", "445")))
        .isFalse();
    assertThat(
            AttackPathKeyMatcher.matches(
                finding("credentials", "no-colon"), key("password", "IS_NOT_NULL", null)))
        .isFalse();
  }

  @Test
  @DisplayName(
      "candidate finding types = the identity type plus every complex type with the key as a"
          + " sub-field")
  void candidate_finding_types() {
    // A `port` key can be satisfied by a primitive port finding OR a complex portscan finding.
    assertThat(AttackPathKeyMatcher.candidateFindingTypes("port"))
        .containsExactlyInAnyOrder("port", "portscan");
    assertThat(AttackPathKeyMatcher.candidateFindingTypes("username"))
        .containsExactlyInAnyOrder("username", "credentials");
    assertThat(AttackPathKeyMatcher.candidateFindingTypes("share_name")).containsExactly("share");
    assertThat(AttackPathKeyMatcher.candidateFindingTypes("file_name")).containsExactly("file");
    assertThat(AttackPathKeyMatcher.candidateFindingTypes("cve")).containsExactly("cve");
    assertThat(AttackPathKeyMatcher.candidateFindingTypes(null)).isEmpty();
  }
}
