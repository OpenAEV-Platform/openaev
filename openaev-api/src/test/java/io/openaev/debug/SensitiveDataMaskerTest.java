package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SensitiveDataMasker")
class SensitiveDataMaskerTest {

  private static final String MASK = "***MASKED***";

  private SensitiveDataMasker maskerWithDefaults() {
    return new SensitiveDataMasker(new DebugProperties().getMasking());
  }

  @Test
  @DisplayName("masks values bound to a sensitive key whatever the value is")
  void masksSensitiveKeys() {
    SensitiveDataMasker masker = maskerWithDefaults();

    assertThat(masker.maskValue("user_password", "hunter2")).isEqualTo(MASK);
    assertThat(masker.maskValue("api_key", "plainlookingvalue")).isEqualTo(MASK);
    assertThat(masker.maskValue("admin.encryption_salt", "ilikesalt")).isEqualTo(MASK);
    assertThat(masker.maskValue("authorization", "anything")).isEqualTo(MASK);
  }

  @Test
  @DisplayName("leaves non-sensitive values untouched")
  void keepsNonSensitiveValues() {
    SensitiveDataMasker masker = maskerWithDefaults();

    assertThat(masker.maskValue("title", "Phishing scenario")).isEqualTo("Phishing scenario");
    assertThat(masker.maskValue("count", 42)).isEqualTo("42");
  }

  @Test
  @DisplayName("masks known secret/PII patterns even with an unknown key")
  void masksValuePatterns() {
    SensitiveDataMasker masker = maskerWithDefaults();

    String jwt =
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    assertThat(masker.maskValue("some_field", jwt)).isEqualTo(MASK);
    assertThat(masker.maskText("token is " + jwt)).doesNotContain("eyJ").contains(MASK);

    assertThat(masker.maskText("contact alice@example.com now"))
        .doesNotContain("alice@example.com")
        .contains(MASK);

    assertThat(masker.maskText("Authorization: Bearer abc.def-123")).doesNotContain("abc.def-123");

    assertThat(masker.maskText("-----BEGIN RSA PRIVATE KEY-----MIIE")).doesNotContain("MIIE...");
    assertThat(masker.maskText("-----BEGIN RSA PRIVATE KEY-----")).contains(MASK);
  }

  @Test
  @DisplayName("does nothing when masking is disabled")
  void disabledMaskingIsPassthrough() {
    DebugProperties.Masking config = new DebugProperties().getMasking();
    config.setEnabled(false);
    SensitiveDataMasker masker = new SensitiveDataMasker(config);

    assertThat(masker.maskValue("password", "hunter2")).isEqualTo("hunter2");
    assertThat(masker.maskText("alice@example.com")).isEqualTo("alice@example.com");
  }

  @Test
  @DisplayName("maskText scans the whole text (statement text is logged in full)")
  void maskTextScansFullText() {
    SensitiveDataMasker masker = maskerWithDefaults();
    // A secret far beyond the per-value scan window must still be masked in free text / SQL.
    String longText = "x".repeat(20_000) + " contact alice@example.com";

    assertThat(masker.maskText(longText)).doesNotContain("alice@example.com").contains(MASK);
  }

  @Test
  @DisplayName("maskStatementText is bounded even when mask-all is off (tail dropped, no leak)")
  void maskStatementTextIsBounded() {
    SensitiveDataMasker masker = maskerWithDefaults(); // mask-all is off by default
    String hugeSql = "select * from t where id in (" + "1,".repeat(10_000) + "alice@example.com)";

    String result = masker.maskStatementText(hugeSql);

    assertThat(result).contains("...(truncated)");
    assertThat(result.length()).isLessThanOrEqualTo(SensitiveDataMasker.MAX_SCAN_LENGTH + 32);
    // The address sits past the cap, so it is dropped rather than scanned and logged.
    assertThat(result).doesNotContain("alice@example.com");
  }

  @Test
  @DisplayName("isSensitiveKey is case-insensitive and substring based")
  void sensitiveKeyDetection() {
    SensitiveDataMasker masker = maskerWithDefaults();

    assertThat(masker.isSensitiveKey("USER_PASSWORD")).isTrue();
    assertThat(masker.isSensitiveKey("clientSecret")).isTrue();
    assertThat(masker.isSensitiveKey("display_name")).isFalse();
    assertThat(masker.isSensitiveKey(null)).isFalse();
  }
}
