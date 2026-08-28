package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the {@code contract_output_element_type} wire contract: every token the client (the AI
 * orchestrator, forking payloads and adding output parsers) emits must deserialize to a {@link
 * ContractOutputType}. A missing or renamed {@link com.fasterxml.jackson.annotation.JsonProperty}
 * used to turn an authored {@code credentials} finding into a rejected request; this test fails
 * fast if any token stops being accepted.
 */
@DisplayName("ContractOutputType accepts every client-emitted token")
class ContractOutputTypeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static Stream<Arguments> clientEmittedTokens() {
    return Stream.of(
        Arguments.of("credentials", ContractOutputType.Credentials),
        Arguments.of("username", ContractOutputType.Username),
        Arguments.of("admin_username", ContractOutputType.AdminUsername),
        Arguments.of("sid", ContractOutputType.Sid),
        Arguments.of("asset", ContractOutputType.Asset),
        Arguments.of("computer", ContractOutputType.Computer),
        Arguments.of("share", ContractOutputType.Share),
        Arguments.of("cve", ContractOutputType.CVE),
        Arguments.of("vulnerability", ContractOutputType.Vulnerability),
        Arguments.of("group", ContractOutputType.Group),
        Arguments.of("file", ContractOutputType.File),
        Arguments.of("delegation", ContractOutputType.Delegation),
        Arguments.of("password_policy", ContractOutputType.PasswordPolicy),
        Arguments.of("asreproastable_account", ContractOutputType.AsreproastableAccount),
        Arguments.of("kerberoastable_account", ContractOutputType.KerberoastableAccount),
        Arguments.of(
            "account_with_password_not_required",
            ContractOutputType.AccountWithPasswordNotRequired),
        Arguments.of("port", ContractOutputType.Port),
        Arguments.of("portscan", ContractOutputType.PortsScan),
        Arguments.of("ipv4", ContractOutputType.IPv4),
        Arguments.of("ipv6", ContractOutputType.IPv6),
        Arguments.of("number", ContractOutputType.Number),
        Arguments.of("text", ContractOutputType.Text),
        Arguments.of("expectation_signature", ContractOutputType.ExpectationSignature),
        Arguments.of("action_output", ContractOutputType.ActionOutput));
  }

  @ParameterizedTest(name = "\"{0}\" deserializes to {1}")
  @MethodSource("clientEmittedTokens")
  void given_clientToken_should_deserializeToExpectedType(String token, ContractOutputType expected)
      throws Exception {
    ContractOutputType parsed = MAPPER.readValue('"' + token + '"', ContractOutputType.class);
    assertEquals(expected, parsed, "token '" + token + "' must map to " + expected);
    assertThat(parsed.getLabel()).isEqualTo(token);
  }

  @ParameterizedTest(name = "\"{0}\" round-trips through JSON")
  @MethodSource("clientEmittedTokens")
  void given_type_should_serializeToItsToken(String token, ContractOutputType type)
      throws Exception {
    assertEquals('"' + token + '"', MAPPER.writeValueAsString(type));
  }

  @org.junit.jupiter.api.Test
  @DisplayName("every enum constant is covered by the client-token contract test")
  void everyConstantIsCovered() {
    long tokenCount = clientEmittedTokens().count();
    // The enum also declares "email", which is not in the client-emitted set exercised above.
    long expectedCovered = Arrays.stream(ContractOutputType.values()).count() - 1;
    assertEquals(
        expectedCovered,
        tokenCount,
        "add any new ContractOutputType to clientEmittedTokens() so the wire contract stays tested");
  }
}
