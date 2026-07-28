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
    // share_name reconciles to file, which is not the port finding's type.
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
}
