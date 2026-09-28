package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.PrimitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Sensitive value masking")
class SensitiveValueMaskingUtilsTest {

  private static final String MASK = SensitiveValueMaskingUtils.MASK;

  @Nested
  @DisplayName("When deriving sensitivity from a contract output type")
  class WhenDerivingSensitivity {

    @Test
    @DisplayName("Should flag the types whose finding value holds a password or a hash")
    void given_aTypeHoldingSecretMaterial_should_flagItSensitive() {
      // -------- Act & Assert --------
      // CredentialsOutputProcessor.toFindingValue concatenates `username:password` (or
      // `username:hash`), so the value really carries the secret.
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.Credentials)).isTrue();
    }

    @Test
    @DisplayName("Should not flag a roastable account, whose value is the username alone")
    void given_aRoastableAccount_should_notFlagItSensitive() {
      // -------- Arrange --------
      // Both recipes declare a `hash` field, but the processors build the finding value from the
      // username alone. The declared composition says so, so the derivation clears them by itself,
      // with no need to list them as exceptions.

      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.AsreproastableAccount))
          .isFalse();
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.KerberoastableAccount))
          .isFalse();
      assertThat(
              SensitiveValueMaskingUtils.maskIfNeeded(
                  ContractOutputType.AsreproastableAccount, "administrator"))
          .isEqualTo("administrator");
      assertThat(
              SensitiveValueMaskingUtils.maskIfNeeded(
                  ContractOutputType.KerberoastableAccount, "svc_sql"))
          .isEqualTo("svc_sql");
    }

    @Test
    @DisplayName("Should not flag types carrying no secret material")
    void given_aTypeWithoutSecretMaterial_should_notFlagItSensitive() {
      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.Text)).isFalse();
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.Port)).isFalse();
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.IPv4)).isFalse();
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.PortsScan)).isFalse();
      // A SID is an identity, not a secret (Product decision on issue 7500).
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.Sid)).isFalse();
    }

    @Test
    @DisplayName("Should never flag a password policy, whose `key` is a setting name")
    void given_aPasswordPolicy_should_notFlagItSensitive() {
      // -------- Arrange --------
      // PasswordPolicy decomposes into PrimitiveType.Key, but that key is a policy setting name
      // (MinimumPasswordLength...), not a cryptographic key: masking it would hide the very
      // information the finding reports.

      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.isSensitive(ContractOutputType.PasswordPolicy))
          .isFalse();
      assertThat(
              SensitiveValueMaskingUtils.maskIfNeeded(
                  ContractOutputType.PasswordPolicy, "MinimumPasswordLength:8"))
          .isEqualTo("MinimumPasswordLength:8");
    }

    @Test
    @DisplayName("Should flag the primitive types holding secret material")
    void given_aPrimitiveType_should_flagOnlySecretOnes() {
      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.isSensitive(PrimitiveType.Password)).isTrue();
      assertThat(SensitiveValueMaskingUtils.isSensitive(PrimitiveType.Hash)).isTrue();
      assertThat(SensitiveValueMaskingUtils.isSensitive(PrimitiveType.Key)).isTrue();
      assertThat(SensitiveValueMaskingUtils.isSensitive(PrimitiveType.Username)).isFalse();
      assertThat(SensitiveValueMaskingUtils.isSensitive(PrimitiveType.Text)).isFalse();
    }

    @Test
    @DisplayName("Should handle a null type as non sensitive")
    void given_aNullType_should_notFlagItSensitive() {
      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.isSensitive((ContractOutputType) null)).isFalse();
      assertThat(SensitiveValueMaskingUtils.isSensitive((PrimitiveType) null)).isFalse();
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded((ContractOutputType) null, "admin:admin"))
          .isEqualTo("admin:admin");
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded((PrimitiveType) null, "admin:admin"))
          .isEqualTo("admin:admin");
    }
  }

  @Nested
  @DisplayName("When the type is not sensitive")
  class WhenNotSensitive {

    @Test
    @DisplayName("Should return the value untouched")
    void given_aNonSensitiveType_should_returnTheValueAsIs() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Text, "admin:Sup3rS3cret");

      // -------- Assert --------
      assertThat(masked).isEqualTo("admin:Sup3rS3cret");
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Text, "plain"))
          .isEqualTo("plain");
    }
  }

  @Nested
  @DisplayName("When the type is sensitive")
  class WhenSensitive {

    @Test
    @DisplayName("Should keep the username of a credential and mask only the secret")
    void given_aCredentialShapedValue_should_maskOnlyTheSecretSegment() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(
              ContractOutputType.Credentials, "jdoe:Sup3rS3cret");

      // -------- Assert --------
      // The username is the actionable half - which account is compromised - and is not a secret.
      // The secret keeps the same two character fragment as anywhere else: masking selectively is
      // not masking harder.
      assertThat(masked).isEqualTo("jdoe:Su" + MASK);
      assertThat(masked).doesNotContain("Sup3rS3cret");
    }

    @Test
    @DisplayName(
        "Should keep a secret containing the separator whole rather than shift the segments")
    void given_aSecretContainingTheSeparator_should_keepItInTheSecretSegment() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, "jdoe:pa:ss");

      // -------- Assert --------
      // The split is limited to the declared segment count: without that limit the parts would
      // shift and the tail of the password would be handed out in the clear as a username. The
      // whole "pa:ss" is one segment, so it is masked as one unit and its tail never surfaces.
      assertThat(masked).isEqualTo("jdoe:pa" + MASK);
      assertThat(masked).doesNotContain("ss");
    }

    @Test
    @DisplayName("Should mask an NTLM hash the same way as a password")
    void given_aCredentialCarryingAHash_should_maskTheSecondSegment() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(
              ContractOutputType.Credentials, "administrator:aad3b435b51404ee");

      // -------- Assert --------
      // Only the membership of TYPE_TO_MASK matters, and Hash belongs to it just like Password.
      assertThat(masked).isEqualTo("administrator:aa" + MASK);
    }

    @Test
    @DisplayName("Should mask the whole value when it does not match the declared composition")
    void given_aValueNotMatchingTheComposition_should_maskItEntirely() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, "no-separator");

      // -------- Assert --------
      // Closed fallback: an unexpected shape can only ever over-mask, never leak.
      assertThat(masked).isEqualTo("no" + MASK);
    }

    @Test
    @DisplayName(
        "Should mask every part when falling back, for a type with no declared composition")
    void given_noDeclaredComposition_should_maskTheWholeValue() {
      // -------- Arrange --------
      // Every type carrying secret material happens to have a composition today, so this exercises
      // the fallback directly: it is the shape a future sensitive type gets until someone declares
      // how its value is built. Masking everything is the safe default.

      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.mask("admin:motdepasse"))
          .isEqualTo("ad" + MASK + ":mo" + MASK);
      assertThat(SensitiveValueMaskingUtils.mask("MIIEvQIBADAN")).isEqualTo("MI" + MASK);
    }

    @Test
    @DisplayName("Should keep a two character fragment of a value without separator")
    void given_aValueWithoutSeparator_should_keepAFragment() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Password, "Sup3rS3cret");

      // -------- Assert --------
      assertThat(masked).isEqualTo("Su" + MASK);
    }

    @Test
    @DisplayName("Should mask a short part entirely")
    void given_aShortPart_should_maskItEntirely() {
      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Hash, "abcd"))
          .isEqualTo(MASK);
      assertThat(
              SensitiveValueMaskingUtils.maskIfNeeded(
                  ContractOutputType.Credentials, "administrator:pwd"))
          .isEqualTo("administrator:" + MASK);
    }

    @Test
    @DisplayName("Should mask a secret segment too short to keep a fragment entirely")
    void given_aShortSecretSegment_should_maskThatSegmentEntirely() {
      // -------- Act --------
      String masked =
          SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, "admin:abcd");

      // -------- Assert --------
      // Selective masking runs each secret segment through the very same maskPart as mask() does,
      // so a segment too short to keep a fragment without disclosing most of it loses it entirely.
      assertThat(masked).isEqualTo("admin:" + MASK);
    }

    @Test
    @DisplayName("Should degenerate into whole-value masking if every segment becomes sensitive")
    void given_everySegmentSensitive_should_behaveExactlyLikeMask() {
      // -------- Arrange --------
      // Selective masking is mask() restricted to the secret segments, not a second mechanism: were
      // Username ever added to TYPE_TO_MASK, the two would produce the same string. Asserting the
      // non-secret segment is the only difference keeps that equivalence visible.
      String value = "administrator:Sup3rS3cret";

      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.mask(value)).isEqualTo("ad" + MASK + ":Su" + MASK);
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, value))
          .isEqualTo("administrator:Su" + MASK);
    }

    @Test
    @DisplayName("Should never split a bare secret, even when it contains the separator")
    void given_aBareSecretContainingASeparator_should_maskItAsOneUnit() {
      // -------- Arrange --------
      // The PrimitiveType path receives a chaining scope variable or a condition target value: the
      // whole value IS the secret. Splitting it would hand out a readable fragment of each part.

      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Password, "abcdef:ghijkl"))
          .isEqualTo("ab" + MASK)
          .doesNotContain("gh");
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Key, "aa:bb:cc:dd"))
          .isEqualTo("aa" + MASK);
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Hash, "p:ss"))
          .isEqualTo(MASK);
    }

    @Test
    @DisplayName("Should mask password, hash and key the same way")
    void given_eachSecretPrimitiveType_should_maskItTheSameWay() {
      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Password, "Secret13"))
          .isEqualTo("Se" + MASK);
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Hash, "ABC123XYZ"))
          .isEqualTo("AB" + MASK);
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Key, "KEY123456890"))
          .isEqualTo("KE" + MASK);
    }

    @Test
    @DisplayName("Should not leak the length of the secret")
    void given_twoSecretsOfDifferentLengths_should_produceTheSameMaskWidth() {
      // -------- Act --------
      String shortSecret = SensitiveValueMaskingUtils.maskIfNeeded(PrimitiveType.Key, "KEY12345");
      String longSecret =
          SensitiveValueMaskingUtils.maskIfNeeded(
              PrimitiveType.Key, "KEY123456789012345678901234567890");

      // -------- Assert --------
      assertThat(shortSecret).isEqualTo(longSecret).isEqualTo("KE" + MASK);
    }

    @Test
    @DisplayName("Should return blank and null values as is")
    void given_aBlankValue_should_returnItAsIs() {
      // -------- Act & Assert --------
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, null))
          .isNull();
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, "  "))
          .isEqualTo("  ");
    }
  }

  @Nested
  @DisplayName("When echoing a masked value back")
  class WhenEchoingAMaskedValue {

    @Test
    @DisplayName("Should recognize the mask of the value currently held")
    void given_theMaskOfTheCurrentValue_should_recognizeIt() {
      // -------- Act & Assert --------
      assertThat(
              SensitiveValueMaskingUtils.isMaskedRepresentationOfCurrentValue(
                  PrimitiveType.Password, "TopSecret", "To" + MASK))
          .isTrue();
    }

    @Test
    @DisplayName("Should not mistake a genuine new value for a masked echo")
    void given_aGenuineValue_should_notRecognizeItAsAnEcho() {
      // -------- Act & Assert --------
      assertThat(
              SensitiveValueMaskingUtils.isMaskedRepresentationOfCurrentValue(
                  PrimitiveType.Password, "TopSecret", "TopSecret"))
          .isFalse();
    }

    @Test
    @DisplayName("Should handle null arguments without recognizing an echo")
    void given_aNullArgument_should_notRecognizeAnEcho() {
      // -------- Act & Assert --------
      assertThat(
              SensitiveValueMaskingUtils.isMaskedRepresentationOfCurrentValue(
                  null, "TopSecret", "To" + MASK))
          .isFalse();
      assertThat(
              SensitiveValueMaskingUtils.isMaskedRepresentationOfCurrentValue(
                  PrimitiveType.Password, null, "To" + MASK))
          .isFalse();
      assertThat(
              SensitiveValueMaskingUtils.isMaskedRepresentationOfCurrentValue(
                  PrimitiveType.Password, "TopSecret", null))
          .isFalse();
    }
  }
}
