package io.openaev.validator;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import jakarta.validation.ConstraintValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Domain Validator Tests")
public class DomainValidatorTest {
  private static final ConstraintValidator<?, String> VALIDATOR = new DomainValidator();

  @Test
  @DisplayName("Valid: domain name with just alphanumerical characters")
  void valid_alphanums() {
    assertThat(VALIDATOR.isValid("myhostname102", null)).isTrue();
  }

  @Test
  @DisplayName("Valid: domain name with just alphanumerical characters")
  void valid_alphanumsWithHyphens() {
    assertThat(VALIDATOR.isValid("myhostname102-appended", null)).isTrue();
  }

  @Test
  @DisplayName("Valid: FQDN with hyphens")
  void valid_fqdnWithHyphens() {
    assertThat(VALIDATOR.isValid("myhostname102.test-appended.test304.test-475", null)).isTrue();
  }

  @Test
  @DisplayName("Invalid: FQDN part ending with hyphens")
  void invalid_fqdnPartEndingWithHyphens() {
    assertThat(VALIDATOR.isValid("myhostname102.test-.test304.test-475", null)).isFalse();
  }

  @Test
  @DisplayName("Invalid: FQDN part starting with hyphens")
  void invalid_fqdnPartStartingWithHyphens() {
    assertThat(VALIDATOR.isValid("myhostname102.-appended.test304.test-475", null)).isFalse();
  }

  @Test
  @DisplayName("Invalid: ending with a dot")
  void invalid_endingWithDot() {
    assertThat(VALIDATOR.isValid("myhostname102.", null)).isFalse();
  }

  @Test
  @DisplayName("Invalid: starting with a dot")
  void invalid_startingWithDot() {
    assertThat(VALIDATOR.isValid(".myhostname102", null)).isFalse();
  }

  @Test
  @DisplayName("Invalid: disallowed characters")
  void invalid_disallowedCharacters() {
    assertThat(VALIDATOR.isValid("hostname spaced %%$$", null)).isFalse();
  }

  @Test
  @DisplayName("Invalid: starting with hyphen")
  void invalid_startingWithHyphen() {
    assertThat(VALIDATOR.isValid("-hostname.invalid", null)).isFalse();
  }

  @Test
  @DisplayName("Invalid: ending with hyphen")
  void invalid_endingWithHyphen() {
    assertThat(VALIDATOR.isValid("hostname.invalid-", null)).isFalse();
  }
}
